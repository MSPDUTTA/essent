package saga

import collection.mutable.HashMap
import java.io.Writer

import firrtl._
import firrtl.Annotations._
import firrtl.ir._
import firrtl.Mappers._

object DevHelpers {
  // assumption: registers can only appear in blocks since whens expanded
  def findRegisters(s: Statement): Seq[DefRegister] = s match {
    case b: Block => b.stmts flatMap findRegisters
    case d: DefRegister => Seq(d)
    case _ => Seq()
  }

  val nodeMap = collection.mutable.HashMap[String, Expression]()

  def lastConnected(s: Statement): Statement = {
    s match {
      case Connect(_, loc, expr) => loc match {
        case w: WRef => nodeMap(w.name) = expr
        case _ =>
      }
      case DefNode(_, name, value) => nodeMap(name) = value
      case _ =>
    }
    s map lastConnected
    s
  }

  def traceRefs(name: String): Expression = nodeMap(name) match {
    case w: WRef => traceRefs(w.name)
    case s => s
  }

  def identifyWE(e: Expression) = e match {
    case m: Mux => m.cond match {
      case w: WRef => w.name
      case s =>
    }
    case e =>
  }

  def generateHarness(circuitName: String, writer: Writer) = {
    val baseStr = s"""|#include <iostream>
                      |
                      |#include "comm_wrapper.h"
                      |#include "$circuitName.h"
                      |
                      |int main() {
                      |  $circuitName dut;
                      |  CommWrapper<$circuitName> comm(dut);
                      |  comm.init_channels();
										  |  comm.init_sim_data();
											|  dut.connect_harness(&comm);
                      |  while (!comm.done()) {
                      |    comm.tick();
                      |  }
                      |  return 0;
                      |}
                      |""".stripMargin
    writer write baseStr
  }
}


class DevTransform extends Transform {
  def execute(circuit: Circuit, annotationMap: AnnotationMap): TransformResult = {
    circuit.modules.head match {
      case m: Module => {
        val registers = DevHelpers.findRegisters(m.body)
        val regNames = registers map (_.name)
        DevHelpers.lastConnected(m.body)
        val lastExp = regNames map DevHelpers.traceRefs
        val writeEnables = lastExp map DevHelpers.identifyWE
        println(regNames zip writeEnables)
      }
      case m: ExtModule =>
    }
    TransformResult(circuit)
  }
}


class EmitCpp(writer: Writer) extends Transform {
  val tabs = "  "
	val regUpdates = scala.collection.mutable.ArrayBuffer.empty[String]

  def genCppType(tpe: Type) = tpe match {
    case UIntType(w) => "uint64_t"
    case SIntType(w) => "sint64_t"
    case _ =>
  }

  def processPort(p: Port): Seq[String] = p.tpe match {
    case ClockType => Seq()
    case _ => Seq(genCppType(p.tpe) + " " + p.name + ";")
  }

  def processExpr(e: Expression): String = e match {
    case w: WRef => w.name
    case u: UIntLiteral => "0x" + u.value.toString(16)
    case m: Mux => {
      val condName = processExpr(m.cond)
      val tvalName = processExpr(m.tval)
      val fvalName = processExpr(m.fval)
      s"$condName ? $tvalName : $fvalName"
    }
    case _ => ""
  }

  def processStmt(s: Statement, registerNames: Set[String]): Seq[String] = s match {
    case b: Block => b.stmts flatMap {s: Statement => processStmt(s, registerNames)}
    case d: DefNode => {
      val lhs = genCppType(d.value.tpe) + " " + d.name
      val rhs = processExpr(d.value)
      Seq(s"$lhs = $rhs;")
    }
    case c: Connect => {
      val lhs = processExpr(c.loc)
      val rhs = processExpr(c.expr)
			val statement = s"$lhs = $rhs;"
			if (registerNames contains lhs) {regUpdates += statement; Seq()}
			else Seq(statement)
    }
    case _ => Seq()
  }

  def makeResetIf(r: DefRegister): String = {
    val regName = r.name
    val resetName = processExpr(r.reset)
    val resetVal = processExpr(r.init)
    s"if ($resetName) $regName = $resetVal;"
  }

	def writeHarnessConnections(m: Module) = {
		val signalDecs = scala.collection.mutable.ArrayBuffer.empty[String]
		val inputDecs = scala.collection.mutable.ArrayBuffer.empty[String]
		val outputDecs = scala.collection.mutable.ArrayBuffer.empty[String]
		m.ports foreach {p => p.tpe match {
			case ClockType =>
			case _ => {
				if (p.name == "reset") signalDecs += s"comm->add_signal(&${p.name});"
				else p.direction match {
					case Input => inputDecs += s"comm->add_in_signal(&${p.name});"
					case Output => outputDecs += s"comm->add_out_signal(&${p.name});"
				}
			}
		}}
		writeLines(1, s"void connect_harness(CommWrapper<struct ${m.name}> *comm) {")
		writeLines(2, inputDecs.reverse)
		writeLines(2, outputDecs.reverse)
		writeLines(2, signalDecs.reverse)
		writer write tabs + "}\n"
	}

	def writeLines(indentLevel: Int, lines: String) {
    writeLines(indentLevel, Seq(lines))
	}

	def writeLines(indentLevel: Int, lines: Seq[String]) {
		lines foreach { s => writer write tabs*indentLevel + s + "\n" }
	}

  def processModule(m: Module) = {
    val registers = DevHelpers.findRegisters(m.body)
    val registerNames = (registers map {r: DefRegister => r.name}).toSet
		val registerDecs = registers map {d: DefRegister => {
      val typeStr = genCppType(d.tpe)
      val regName = d.name
      s"$typeStr $regName;"
    }}

    val modName = m.name
    val headerGuardName = modName.toUpperCase + "_H_"

    writeLines(0, s"#ifndef $headerGuardName")
    writeLines(0, s"#define $headerGuardName")
		writeLines(0, "")
    writeLines(0, s"typedef struct $modName {")
		writeLines(1, registerDecs)
    writeLines(1, m.ports flatMap processPort)
		writeLines(0, "")
    writeLines(1, "void eval(bool update_registers) {")
		writeLines(2, processStmt(m.body, registerNames))
		writeLines(2, "if (!update_registers)")
		writeLines(3, "return;")
		writeLines(2, regUpdates)
    writeLines(2, registers map makeResetIf)
    writeLines(1, "}")
		writeLines(0, "")
		writeHarnessConnections(m)
    writeLines(0, s"} $modName;")
    writeLines(0, s"#endif  // $headerGuardName")
  }

  def execute(circuit: Circuit, annotationMap: AnnotationMap): TransformResult = {
    circuit.modules foreach {
      case m: Module => processModule(m)
      case m: ExtModule =>
    }
    println(circuit.serialize)
    TransformResult(circuit)
  }
}

class CCCompiler extends Compiler {
  def transforms(writer: Writer): Seq[Transform] = Seq(
    new firrtl.Chisel3ToHighFirrtl,
    new firrtl.IRToWorkingIR,
    new firrtl.ResolveAndCheck,
    new firrtl.HighFirrtlToMiddleFirrtl,
    new firrtl.passes.InferReadWrite(TransID(-1)),
    new firrtl.passes.ReplSeqMem(TransID(-2)),
    new firrtl.MiddleFirrtlToLowFirrtl,
    new firrtl.passes.InlineInstances(TransID(0)),
    // new DevTransform,
    new EmitCpp(writer)
    // new firrtl.EmitFirrtl(writer)
  )
}
