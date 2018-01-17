// Unused code that should soon be deleted


// from Compiler.scala

// def writeBodyWithZones(bodyEdges: Seq[HyperedgeDep], regNames: Seq[String],
//                        allRegUpdates: Seq[String], resetTree: Seq[String],
//                        topName: String, otherDeps: Seq[String],
//                        doNotShadow: Seq[String]) {
//   val trackActivity = false
//   // map of name -> original hyperedge
//   val heMap = (bodyEdges map { he => (he.name, he) }).toMap
//   // calculate zones based on all edges
//   val allZones = buildGraph(bodyEdges).findZones(regNames)
//   val zoneMap = allZones filter { case (k,v) => v.size > 10}
//   // set of all nodes in zones
//   val nodesInZones = zoneMap.values.flatten.toSet
//   println(s"Nodes in zones: ${nodesInZones.size}")
//   // map of zone name -> zone edges (easy) - needed?
//   val zoneEdges = zoneMap map {case (k,v) => (k, v filter {heMap.contains} map {heMap})}
//   // seq of edges not in zones
//   val nonZoneEdges = bodyEdges filter { he => !nodesInZones.contains(he.name) }
//   // set of all dependences from non-zone edges
//   val nonZoneDeps = (nonZoneEdges map { _.deps }).flatten.toSet ++ otherDeps.toSet
//   // output nodes (intersection of deps and zone nodes)
//   val zoneOutputs = nonZoneDeps.intersect(nodesInZones) filter {!regNames.contains(_)}
//   val doNotDec = zoneOutputs.toSet
//   // predeclare output nodes
//   val outputTypes = zoneOutputs.toSeq map {name => findResultType(heMap(name).stmt)}
//   val outputPairs = (outputTypes zip zoneOutputs).toSeq
//   val preDecs = outputPairs map {case (tpe, name) => s"${genCppType(tpe)} $name;"}
//   writeLines(0, preDecs)
//   // activity tracking
//   if (trackActivity) {
//     writeLines(0, "uint64_t total_transitions = 0;")
//     writeLines(0, "uint64_t total_zones_active = 0;")
//     writeLines(0, "uint64_t cycles_ticked = 0;")
//   }
//   // start emitting eval function
//   writeLines(0, s"void $topName::eval(bool update_registers, bool verbose, bool done_reset) {")
//   writeLines(1, resetTree)
//   // predeclare zone activity flags
//   writeLines(1, (zoneMap.keys map { zoneName => s"bool ${genFlagName(zoneName)} = reset;"}).toSeq)
//   // emit update checks
//   zoneMap foreach { case (zoneName, zoneContents) => {
//     writeLines(1, s"if ($zoneName != $zoneName$$next) ${genFlagName(zoneName)} = true;")
//     zoneContents filter { name => regNames.contains(name) } foreach { name =>
//       writeLines(1, s"if ($name != $name$$next) ${genFlagName(zoneName)} = true;")}
//   }}
//   // emit reg updates
//   if (!allRegUpdates.isEmpty || trackActivity) {
//     writeLines(1, "if (update_registers) {")
//     if (trackActivity) {
//       writeRegActivityTracking(regNames)
//       writeZoneActivityTracking(zoneMap.keys.toSeq)
//     }
//     writeLines(2, allRegUpdates)
//     writeLines(1, "}")
//   }
//   // emit each zone
//   zoneMap.keys foreach { zoneName => {
//     writeLines(1, s"if (${genFlagName(zoneName)}) {")
//     writeBody(2, zoneEdges(zoneName), doNotShadow ++ doNotDec, doNotDec)
//     // val zoneGraph = buildGraph(zoneEdges(zoneName))
//     // writeLines(2, zoneGraph.reorderCommands flatMap emitStmt(doNotDec))
//     writeLines(1, s"}")
//   }}
//   // emit body (without redeclaring)
//   writeBody(1, nonZoneEdges, doNotShadow, doNotDec)
// }



// def compressFlags(zoneToInputs: Map[String, Seq[String]]): Map[String,String] = {
//   val allInputZonePairs = zoneToInputs flatMap {
//     case (name, inputs) => inputs map { (_, name) }
//   }
//   val inputToConsumingZones = allInputZonePairs.groupBy(_._1).map {
//     case (input, inputZonePairs) => (input, inputZonePairs.map(_._2))
//   }
//   val allInputs = zoneToInputs.values.flatten.toSet.toSeq
//   val numChecksOrig = zoneToInputs.values.flatten.size
//   println(s"There are ${allInputs.size} distinct zone inputs used in $numChecksOrig checks")
//   val sigToMaxIntersects = (allInputs map { sigName => {
//     val childZones = inputToConsumingZones(sigName)
//     val consistentCompanions = childZones map zoneToInputs map { _.toSet} reduceLeft { _.intersect(_) }
//     (sigName, consistentCompanions)
//   }}).toMap
//   val confirmedSubsets = (allInputs groupBy sigToMaxIntersects).values filter { _.size > 1 }
//   // FUTURE: think this is still leaving out a couple partial overlap subsets
//   println(s"Agreed on ${confirmedSubsets.size} subsets")
//   val renames = (confirmedSubsets flatMap {
//     subset => subset map { sigName => (sigName, subset.head + "$shared") }
//   }).toMap
//   val flagsAfterCompression = (allInputs map { sigName => renames.getOrElse(sigName, sigName) }).distinct
//   val numInputsAfterCompression = (zoneToInputs.values map {
//     zoneInputs => (zoneInputs map { sigName => renames.getOrElse(sigName, sigName) }).distinct
//   }).flatten.size
//   println(s"Could be ${flagsAfterCompression.size} distinct zone flags used in $numInputsAfterCompression checks")
//   // println(s"${confirmedSubsets.flatten.size} ${confirmedSubsets.flatten.toSet.size}")
//   renames
// }



// def renameAndUnique(origList: Seq[String], renames: Map[String,String]) = {
//   val renamed = origList map { name => renames.getOrElse(name, name) }
//   renamed.distinct
// }



// def writeBodyWithZonesML(bodyEdges: Seq[HyperedgeDep], regNames: Seq[String],
//                          regDefs: Seq[DefRegister], resetTree: Seq[String],
//                          topName: String, otherDeps: Seq[String],
//                          doNotShadow: Seq[String], memUpdates: Seq[MemUpdate],
//                          extIOtypes: Map[String, Type]) {
//   val trackActivity = false
//   val exportSparsity = false
//   // map of name -> original hyperedge
//   val heMap = (bodyEdges map { he => (he.name, he) }).toMap
//   val regNamesSet = regNames.toSet
//   // printMuxSimilarity(bodyEdges)
//
//   // calculate zones based on all edges
//   val g = buildGraph(bodyEdges)
//   // val zoneMapWithSources = g.findZonesTopo3(regNames, doNotShadow)
//   // val zoneMapWithSources = g.findZonesKern(regNames, doNotShadow)
//   // val zoneMapWithSources = g.findZonesML(regNames, doNotShadow)
//   val zoneMapWithSources = g.findZonesMFFC(regNames, doNotShadow)
//   val zoneMap = zoneMapWithSources filter { _._1 != "ZONE_SOURCE" }
//   // g.writeZoneInfo("mffcs.zones", zoneMapWithSources)
//   g.analyzeZoningQuality(zoneMap)
//   // g.printDeadRegisters(regNames, otherDeps)
//   val flagRenames = compressFlags(zoneMap.mapValues(_.inputs))
//   val inputsToZones = zoneMap.flatMap(_._2.inputs).toSet
//   val nodesInZones = zoneMap.flatMap(_._2.members).toSet
//   val nodesInZonesWithSources = zoneMapWithSources.flatMap(_._2.members).toSet
//   val outputsFromZones = zoneMap.flatMap(_._2.outputs).toSet.diff(regNamesSet)
//
//   // sparsity output
//   val zoneStmtOutputOrder = scala.collection.mutable.ArrayBuffer[String]()
//   if (exportSparsity) {
//     g.writeCOOFile("rocketchip.coo")
//     g.writeCOOFile("rocketchip.topo.coo", Option(g.reorderNames.toSeq))
//   }
//
//   // predeclare output nodes
//   val outputTypes = outputsFromZones.toSeq map {name => findResultType(heMap(name).stmt)}
//   val outputPairs = (outputTypes zip outputsFromZones).toSeq
//   val preDecs = outputPairs map {case (tpe, name) => s"${genCppType(tpe)} $name;"}
//   writeLines(0, preDecs)
//   // activity tracking
//   if (trackActivity) {
//     writeLines(0, "uint64_t total_transitions = 0;")
//     writeLines(0, "uint64_t total_zones_active = 0;")
//     writeLines(0, "uint64_t cycles_ticked = 0;")
//     writeLines(0, "uint64_t outputs_checked = 0;")
//     writeLines(0, "uint64_t outputs_silenced = 0;")
//     // val zoneActCounts = zoneMap.keys map genFlagName map {
//     //   zoneName => s"uint64_t ${zoneName}_ACTS = 0;"
//     // }
//     // writeLines(0, zoneActCounts.toSeq)
//   }
//   val doNotDec = outputsFromZones.toSet
//   println(s"Output nodes: ${outputsFromZones.size}")
//
//   // set input flags to true for other inputs (resets, mems, or external IOs)
//   // FUTURE: remove. should make change detection for these inputs so consuming
//   //         zones have a chance to sleep
//   val otherFlags = inputsToZones diff (regNamesSet ++ zoneMapWithSources.flatMap(_._2.outputs).toSet)
//   val memNames = memUpdates map { _.memName }
//   val memFlags = otherFlags intersect memNames.toSet
//   val memWriteTrackDecs = memFlags map {
//     flagName => s"bool WTRACK_${flagName.replace('.','$')};"
//   }
//   writeLines(0, memWriteTrackDecs.toSeq)
//   val nonMemFlags = otherFlags diff memNames.toSet
//   // FUTURE: fix, can't be hacking for reset, but reset isn't in signal map table
//   val nonMemFlagTypes = nonMemFlags.toSeq map {
//     name => if (name.endsWith("reset")) UIntType(IntWidth(1)) else extIOtypes(name)
//   }
//   val nonMemPreDecs = (nonMemFlagTypes zip nonMemFlags.toSeq) map {
//     case (tpe, name) => s"${genCppType(tpe)} ${name.replace('.','$')}$$old;"
//   }
//   writeLines(0, nonMemPreDecs)
//
//   writeLines(0, s"bool sim_cached = false;")
//
//   // start emitting eval function
//   writeLines(0, s"void $topName::eval(bool update_registers, bool verbose, bool done_reset) {")
//   writeLines(1, resetTree)
//   // predeclare zone activity flags
//   val nonRegActSigs = (inputsToZones diff regNamesSet).toSeq
//   val nonRegActSigsCompressed = renameAndUnique(nonRegActSigs, flagRenames)
//   val inputRegs = (regNamesSet intersect inputsToZones).toSeq
//   val inputRegsCompressed = ((renameAndUnique(inputRegs, flagRenames)).toSet -- nonRegActSigsCompressed.toSet).toSeq
//   val otherRegs = (regNamesSet diff inputRegs.toSet).toSeq
//   println(s"Unzoned regs: ${otherRegs.size}")
//   val nonRegActFlagDecs = nonRegActSigsCompressed map {
//     sigName => s"bool ${genFlagName(sigName)} = !sim_cached;"
//   }
//   writeLines(1, nonRegActFlagDecs)
//   writeLines(1, inputRegsCompressed map { regName => s"bool ${genFlagName(regName)};" })
//   println(s"Activity flags: ${renameAndUnique(inputsToZones.toSeq, flagRenames).size}")
//   writeLines(1, yankRegResets(regDefs))
//
//   // emit reg updates (with update checks)
//   if (!regDefs.isEmpty || trackActivity) {
//     if (trackActivity) {
//       // writeZoneActivityTracking(zoneMap.keys.toSeq)
//       writeLines(1, s"const uint64_t num_zones = ${zoneMap.size};")
//       writeLines(1, s"uint64_t zones_active = 0;")
//     }
//     // intermixed
//     writeLines(1, "if (update_registers && sim_cached) {")
//     if (trackActivity) {
//       writeRegActivityTracking(regNames)
//     }
//     val checkAndUpdates = inputRegs flatMap {
//       regName => Seq(s"${genFlagName(regName, flagRenames)} |= $regName != $regName$$next;",
//                      s"$regName = $regName$$next;")
//     }
//     writeLines(2, checkAndUpdates)
//     writeLines(2, otherRegs map { regName => s"$regName = $regName$$next;"})
//     // writeLines(2, regDefs map emitRegUpdate)
//     writeLines(1, "} else if (update_registers) {")
//     writeLines(2, regNames map { regName => s"$regName = $regName$$next;"})
//     writeLines(2, inputRegsCompressed map { regName => s"${genFlagName(regName, flagRenames)} = true;"})
//     writeLines(1, "} else if (sim_cached) {")
//     // FUTURE: for safety, should this be regNames (instead of inputRegs)
//     writeLines(2, inputRegsCompressed map { regName => s"${genFlagName(regName, flagRenames)} |= false;"})
//     writeLines(1, "}")
//   }
//   writeLines(1, "sim_cached = !reset;")
//
//   // set input flags to true for mem inputs
//   // FUTURE: if using mem name for hashing, what if multiple write ports?
//   val memEnablesAndMasks = (memUpdates map {
//     mu => (mu.memName, Seq(mu.wrEnName, mu.wrMaskName))
//   }).toMap
//   val memFlagsTrue = memFlags map {
//     flagName => s"${genFlagName(flagName, flagRenames)} = true;"
//   }
//   val memChangeDetects = memFlags map { flagName => {
//     val trackerName = s"WTRACK_${flagName.replace('.','$')}"
//     s"${genFlagName(flagName, flagRenames)} |= $trackerName;"
//   }}
//   writeLines(1, memChangeDetects.toSeq)
//
//   // do activity detection on other inputs (external IOs and resets)
//   val nonMemChangeDetects = nonMemFlags map { sigName => {
//     val oldVersion = s"${sigName.replace('.','$')}$$old"
//     val flagName = genFlagName(sigName, flagRenames)
//     s"$flagName |= $sigName != $oldVersion;"
//   }}
//   writeLines(1, nonMemChangeDetects.toSeq)
//   // cache old versions
//   val nonMemCaches = nonMemFlags map { sigName => {
//     val oldVersion = s"${sigName.replace('.','$')}$$old"
//     s"$oldVersion = $sigName;"
//   }}
//   writeLines(1, nonMemCaches.toSeq)
//   // val zoneDescendants = findZoneDescendants(memFlags.toSet, zoneMap)
//   // println(s"Descended from true flags: ${zoneDescendants.size}")
//
//   // compute zone order
//   // map of name -> zone name (if in zone)
//   val nameToZoneName = zoneMap flatMap {
//     case (zoneName, Graph.ZoneInfo(inputs, members, outputs)) => {
//       outputs map { portName => (portName, zoneName) }
//   }}
//   // list of super hyperedges for zones
//   val zoneSuperEdges = zoneMap map {
//     case (zoneName, Graph.ZoneInfo(inputs, members, outputs)) => {
//       HyperedgeDep(zoneName, inputs, heMap(members.head).stmt)
//   }}
//   // list of non-zone hyperedges
//   val nonZoneEdges = bodyEdges filter { he => !nodesInZonesWithSources.contains(he.name) }
//   // list of hyperedges with zone members replaced with zone names
//   val topLevelHE = zoneSuperEdges map { he:HyperedgeDep => {
//     val depsRenamedForZones = (he.deps map {
//       depName => nameToZoneName.getOrElse(depName, depName)
//     }).distinct
//     HyperedgeDep(he.name, depsRenamedForZones, he.stmt)
//   }}
//   // reordered names
//   val gTopLevel = buildGraph(topLevelHE.toSeq)
//   val zonesReordered = gTopLevel.reorderNames
//   // gTopLevel.writeDotFile("zonegraph.dot")
//
//   // emit zone of sources
//   if (zoneMapWithSources.contains("ZONE_SOURCE")) {
//     val sourceZoneInfo = zoneMapWithSources("ZONE_SOURCE")
//     val sourceZoneEdges = sourceZoneInfo.members map heMap
//     writeBody(1, sourceZoneEdges, doNotShadow ++ doNotDec ++ sourceZoneInfo.outputs, doNotDec)
//     if (exportSparsity) zoneStmtOutputOrder ++= buildGraph(sourceZoneEdges.toSeq).reorderNames
//   }
//
//   // stash of ability to do get register depths
//   // addMemDepsToGraph(g, memUpdates)
//   // val stateDepths = g.findStateDepths(regNames ++ memNames, extIOtypes.keys.toSeq)
//
//   // emit each zone
//   // zonesReordered map zoneMap foreach { case Graph.ZoneInfo(inputs, members, outputs) => {
//   zonesReordered map { zoneName => (zoneName, zoneMap(zoneName)) } foreach {
//       case (zoneName, Graph.ZoneInfo(inputs, members, outputs)) => {
//     val sensitivityListStr = renameAndUnique(inputs, flagRenames) map genFlagName mkString(" || ")
//     if (sensitivityListStr.isEmpty)
//       writeLines(1, s"{")
//     else
//       writeLines(1, s"if ($sensitivityListStr) {")
//     if (trackActivity) {
//       writeLines(2, "zones_active++;")
//       // writeLines(2, s"${genFlagName(zoneName)}_ACTS++;")
//     }
//     val outputsCleaned = (outputs.toSet intersect inputsToZones diff regNamesSet).toSeq
//     val outputTypes = outputsCleaned map {name => findResultType(heMap(name).stmt)}
//     val oldOutputs = outputsCleaned zip outputTypes map {case (name, tpe) => {
//       s"${genCppType(tpe)} $name$$old = $name;"
//     }}
//     writeLines(2, oldOutputs)
//     val zoneEdges = (members.toSet diff regNamesSet).toSeq map heMap
//     writeBody(2, zoneEdges, doNotShadow ++ doNotDec, doNotDec)
//     if (trackActivity) {
//       writeLines(2, s"outputs_checked += ${outputsCleaned.size};")
//       val outputsSilenced = outputsCleaned map {
//         name => s"if ($name == $name$$old) outputs_silenced++;"
//       }
//       writeLines(2, outputsSilenced)
//     }
//     val outputChangeDetections = outputsCleaned map {
//       name => s"${genFlagName(name, flagRenames)} |= $name != $name$$old;"
//     }
//     writeLines(2, outputChangeDetections)
//     writeLines(1, "}")
//     if (exportSparsity) zoneStmtOutputOrder ++= buildGraph(zoneEdges.toSeq).reorderNames
//   }}
//
//   if (trackActivity) {
//     writeLines(2, s"total_zones_active += zones_active;")
//     writeLines(2, s"""printf("Zones Active %llu/%llu\\n", zones_active, num_zones);""")
//     writeLines(2, s"""printf("Average Zones: %g\\n", (double) total_zones_active/cycles_ticked);""")
//     writeLines(2, s"""printf("Outputs Silenced: %llu/%llu\\n", outputs_silenced, outputs_checked);""")
//   }
//
//   // emit rest of body (without redeclaring)
//   writeBody(1, nonZoneEdges, doNotShadow, doNotDec)
//   if (exportSparsity) {
//     zoneStmtOutputOrder ++= buildGraph(nonZoneEdges.toSeq).reorderNames
//     g.writeCOOFile("rocketchip.zones.coo", Option(zoneStmtOutputOrder.toSeq))
//   }
//
//   // printZoneStateAffinity(zoneMapWithSources, regNames, memUpdates)
//
//   val memWriteTrackerUpdates = memFlags map { flagName => {
//     val trackerName = s"WTRACK_${flagName.replace('.','$')}"
//     val condition = memEnablesAndMasks(flagName).mkString(" && ");
//     s"$trackerName = $condition;"
//   }}
//   writeLines(1, memWriteTrackerUpdates.toSeq)
//
//   // if (trackActivity) {
//   //   writeLines(1, "if (ZONE_SimDTM_1$exit) {")
//   //   val zoneActCountsPrints = zoneMap.keys map genFlagName map {
//   //     zoneName => s"""printf("${zoneName}: %llu\\n", ${zoneName}_ACTS);"""
//   //   }
//   //   writeLines(2, zoneActCountsPrints.toSeq)
//   //   writeLines(1, "}")
//   // }
// }



// def emitEval(topName: String, circuit: Circuit) = {
//   val simpleOnly = false
//   val topModule = findModule(circuit.main, circuit) match {case m: Module => m}
//   val allInstances = Seq((topModule.name, "")) ++
//     findAllModuleInstances("", circuit)(topModule.body)
//   val module_results = allInstances map {
//     case (modName, prefix) => findModule(modName, circuit) match {
//       case m: Module => emitBody(m, circuit, prefix)
//       case em: ExtModule => (Seq(), EmptyStmt, Seq())
//     }
//   }
//   val extIOs = allInstances flatMap {
//     case (modName, prefix) => findModule(modName, circuit) match {
//       case m: Module => Seq()
//       case em: ExtModule => { em.ports map {
//         port => (s"$prefix${port.name}", port.tpe)
//       }}
//     }
//   }
//   val resetTree = buildResetTree(allInstances, circuit)
//   val (allRegDefs, allBodies, allMemUpdates) = module_results.unzip3
//   val allDeps = allBodies flatMap findDependencesStmt
//   val (otherDeps, prints, stops) = separatePrintsAndStops(allDeps)
//   val regNames = allRegDefs.flatten map { _.name }
//   val memDeps = (allMemUpdates.flatten) flatMap findDependencesMemWrite
//   val pAndSDeps = (prints ++ stops) flatMap { he => he.deps }
//   writeLines(0, "")
//   // decRegActivityTracking(regNames)
//   // writeLines(0, "")
//   if (simpleOnly) {
//     writeLines(0, s"void $topName::eval(bool update_registers, bool verbose, bool done_reset) {")
//     writeLines(1, resetTree)
//     // emit reg updates
//     if (!allRegDefs.isEmpty) {
//       writeLines(1, "if (update_registers) {")
//       // recRegActivityTracking(regNames)
//       writeLines(2, allRegDefs.flatten map emitRegUpdate)
//       writeLines(1, "}")
//     }
//     writeBodySimple(1, otherDeps, regNames)
//     // writeBody(1, otherDeps, (regNames ++ memDeps ++ pAndSDeps).distinct, regNames.toSet)
//   } else {
//     writeBodyWithZonesML(otherDeps, regNames, allRegDefs.flatten, resetTree,
//                          topName, memDeps ++ pAndSDeps, (regNames ++ memDeps ++ pAndSDeps).distinct,
//                          allMemUpdates.flatten, extIOs.toMap)
//   }
//   if (!prints.isEmpty || !stops.isEmpty) {
//     writeLines(1, "if (done_reset && update_registers) {")
//     if (!prints.isEmpty) {
//       writeLines(2, "if(verbose) {")
//       writeLines(3, (prints map {dep => dep.stmt} flatMap emitStmt(Set())))
//       writeLines(2, "}")
//     }
//     writeLines(2, (stops map {dep => dep.stmt} flatMap emitStmt(Set())))
//     writeLines(1, "}")
//   }
//   writeLines(1, allMemUpdates.flatten map emitMemUpdate)
//   writeLines(0, "}")
//   writeLines(0, "")
//   // printRegActivityTracking(regNames)
// }



// def writeBodyWithZonesMLTail(bodyEdges: Seq[HyperedgeDep], regNames: Seq[String],
//                          regDefs: Seq[DefRegister], resetTree: Seq[String],
//                          topName: String, otherDeps: Seq[String],
//                          doNotShadow: Seq[String], memUpdates: Seq[MemUpdate],
//                          extIOtypes: Map[String, Type]): Seq[String] = {
//   // map of name -> original hyperedge
//   val heMap = (bodyEdges map { he => (he.name, he) }).toMap
//   val regNamesSet = regNames.toSet
//   // calculate zones based on all edges
//   val g = buildGraph(bodyEdges)
//   val zoneMapWithSources = g.findZonesMFFC(regNames, doNotShadow)
//   // val zoneMapWithSources = Map[String, Graph.ZoneInfo]()
//   val zoneMap = zoneMapWithSources filter { _._1 != "ZONE_SOURCE" }
//   g.analyzeZoningQuality(zoneMap)
//   val flagRenames = compressFlags(zoneMap.mapValues(_.inputs))
//   val inputsToZones = zoneMap.flatMap(_._2.inputs).toSet
//   val nodesInZones = zoneMap.flatMap(_._2.members).toSet
//   val nodesInZonesWithSources = zoneMapWithSources.flatMap(_._2.members).toSet
//   val outputsFromZones = zoneMap.flatMap(_._2.outputs).toSet.diff(regNamesSet)
//
//   // predeclare output nodes
//   val outputTypes = outputsFromZones.toSeq map {name => findResultType(heMap(name).stmt)}
//   val outputPairs = (outputTypes zip outputsFromZones).toSeq
//   // val noPermSigs = outputPairs filter { !_._2.contains('.') }
//   val preDecs = outputPairs map {case (tpe, name) => s"${genCppType(tpe)} $name;"}
//   writeLines(0, preDecs)
//
//   val doNotDec = outputsFromZones.toSet
//   println(s"Output nodes: ${outputsFromZones.size}")
//
//   // set input flags to true for other inputs (resets, mems, or external IOs)
//   // FUTURE: remove. should make change detection for these inputs so consuming
//   //         zones have a chance to sleep
//   val otherFlags = inputsToZones diff (regNamesSet ++ zoneMapWithSources.flatMap(_._2.outputs).toSet)
//   val memNames = memUpdates map { _.memName }
//   val memFlags = otherFlags intersect memNames.toSet
//   val memWriteTrackDecs = memFlags map {
//     flagName => s"bool WTRACK_${flagName.replace('.','$')};"
//   }
//   writeLines(0, memWriteTrackDecs.toSeq)
//   val nonMemFlags = otherFlags diff memNames.toSet
//   // FUTURE: fix, can't be hacking for reset, but reset isn't in signal map table
//   val nonMemFlagTypes = nonMemFlags.toSeq map {
//     name => if (name.endsWith("reset")) UIntType(IntWidth(1)) else extIOtypes(name)
//   }
//   val nonMemPreDecs = (nonMemFlagTypes zip nonMemFlags.toSeq) map {
//     case (tpe, name) => s"${genCppType(tpe)} ${name.replace('.','$')}$$old;"
//   }
//   writeLines(0, nonMemPreDecs)
//
//   // predeclare zone activity flags
//   val nonRegActSigs = (inputsToZones diff regNamesSet).toSeq
//   val nonRegActSigsCompressed = renameAndUnique(nonRegActSigs, flagRenames)
//   val inputRegs = (regNamesSet intersect inputsToZones).toSeq
//   val inputRegsCompressed = ((renameAndUnique(inputRegs, flagRenames)).toSet -- nonRegActSigsCompressed.toSet).toSeq
//   val regsUnreadByZones = (regNamesSet diff inputRegs.toSet).toSeq
//   println(s"Regs unread by zones: ${regsUnreadByZones.size}")
//   val regsSetInZones = (regNamesSet intersect nodesInZones).toSeq
//   val regsUnsetByZones = (regNamesSet diff inputRegs.toSet).toSeq
//   println(s"Regs unset by zones: ${regsUnsetByZones.size}")
//   val allFlags = nonRegActSigsCompressed ++ inputRegsCompressed
//   writeLines(0, allFlags map { sigName => s"bool ${genFlagName(sigName)};" })
//
//   writeLines(0, s"bool sim_cached = false;")
//   writeLines(0, s"bool regs_set = false;")
//
//   // start emitting eval function
//   writeLines(0, s"void $topName::eval(bool update_registers, bool verbose, bool done_reset) {")
//   writeLines(1, resetTree)
//
//   writeLines(1, "if (!sim_cached) {")
//   writeLines(2, allFlags map { sigName => s"${genFlagName(sigName)} = true;" })
//   writeLines(1, "}")
//
//   // val nonRegActFlagDecs = nonRegActSigsCompressed map {
//   //   sigName => s"${genFlagName(sigName)} = !sim_cached;"
//   // }
//   // writeLines(1, nonRegActFlagDecs)
//   // writeLines(1, inputRegsCompressed map { regName => s"${genFlagName(regName)} = true;" })
//   println(s"Activity flags: ${renameAndUnique(inputsToZones.toSeq, flagRenames).size}")
//
//   // emit reg updates (with update checks)
//   // if (!regDefs.isEmpty) {
//     // intermixed
//     // writeLines(1, "if (update_registers && sim_cached) {")
//     // writeLines(1, "if (update_registers) {")
//     // val checkAndUpdates = inputRegs flatMap {
//     //   regName => Seq(s"${genFlagName(regName, flagRenames)} |= $regName != $regName$$next;",
//     //                  s"$regName = $regName$$next;")
//     // }
//     // writeLines(2, checkAndUpdates)
//     // writeLines(2, otherRegs map { regName => s"$regName = $regName$$next;"})
//     // // writeLines(2, regDefs map emitRegUpdate)
//     // writeLines(1, "} else if (update_registers) {")
//     // writeLines(2, regNames map { regName => s"$regName = $regName$$next;"})
//     // writeLines(2, inputRegsCompressed map { regName => s"${genFlagName(regName, flagRenames)} = true;"})
//     // writeLines(1, "} else if (sim_cached) {")
//     // // FUTURE: for safety, should this be regNames (instead of inputRegs)
//     // writeLines(2, inputRegsCompressed map { regName => s"${genFlagName(regName, flagRenames)} |= false;"})
//     // writeLines(1, "}")
//   // }
//   writeLines(1, "sim_cached = regs_set;")
//
//   // set input flags to true for mem inputs
//   // FUTURE: if using mem name for hashing, what if multiple write ports?
//   val memEnablesAndMasks = (memUpdates map {
//     mu => (mu.memName, Seq(mu.wrEnName, mu.wrMaskName))
//   }).toMap
//   val memFlagsTrue = memFlags map {
//     flagName => s"${genFlagName(flagName, flagRenames)} = true;"
//   }
//   val memChangeDetects = memFlags map { flagName => {
//     val trackerName = s"WTRACK_${flagName.replace('.','$')}"
//     s"${genFlagName(flagName, flagRenames)} |= $trackerName;"
//   }}
//   writeLines(1, memChangeDetects.toSeq)
//
//   // do activity detection on other inputs (external IOs and resets)
//   val nonMemChangeDetects = nonMemFlags map { sigName => {
//     val oldVersion = s"${sigName.replace('.','$')}$$old"
//     val flagName = genFlagName(sigName, flagRenames)
//     s"$flagName |= $sigName != $oldVersion;"
//   }}
//   writeLines(1, nonMemChangeDetects.toSeq)
//   // cache old versions
//   val nonMemCaches = nonMemFlags map { sigName => {
//     val oldVersion = s"${sigName.replace('.','$')}$$old"
//     s"$oldVersion = $sigName;"
//   }}
//   writeLines(1, nonMemCaches.toSeq)
//
//   // compute zone order
//   // map of name -> zone name (if in zone)
//   val nameToZoneName = zoneMap flatMap {
//     case (zoneName, Graph.ZoneInfo(inputs, members, outputs)) => {
//       outputs map { portName => (portName, zoneName) }
//   }}
//   // list of super hyperedges for zones
//   val zoneSuperEdges = zoneMap map {
//     case (zoneName, Graph.ZoneInfo(inputs, members, outputs)) => {
//       HyperedgeDep(zoneName, inputs, heMap(members.head).stmt)
//   }}
//   // list of non-zone hyperedges
//   val nonZoneEdges = bodyEdges filter { he => !nodesInZonesWithSources.contains(he.name) }
//   // list of hyperedges with zone members replaced with zone names
//   val topLevelHE = zoneSuperEdges map { he:HyperedgeDep => {
//     val depsRenamedForZones = (he.deps map {
//       depName => nameToZoneName.getOrElse(depName, depName)
//     }).distinct
//     HyperedgeDep(he.name, depsRenamedForZones, he.stmt)
//   }}
//   // reordered names
//   val gTopLevel = buildGraph(topLevelHE.toSeq)
//   val zonesReordered = gTopLevel.reorderNames
//
//   // determine last use of flags
//   val flagNameZoneNameTuples = zonesReordered flatMap { zoneName => {
//     val rawInputs = zoneMap(zoneName).inputs
//     val flagsUsed = renameAndUnique(rawInputs, flagRenames)
//     flagsUsed map { (_, zoneName) }
//   }}
//   val flagToConsumingZones = flagNameZoneNameTuples groupBy { _._1 } mapValues { _ map {_._2} }
//   val flagToLastZone = flagToConsumingZones map {
//     case (flagName, consumingZones) => (flagName, consumingZones.last)
//   }
//   val zoneToLastFlags = flagToLastZone.keys groupBy { flagToLastZone(_) }
//
//   // emit zone of sources
//   if (zoneMapWithSources.contains("ZONE_SOURCE")) {
//     val sourceZoneInfo = zoneMapWithSources("ZONE_SOURCE")
//     val sourceZoneEdges = sourceZoneInfo.members map heMap
//     // FUTURE: does this need to be made into tail?
//     writeBody(1, sourceZoneEdges, doNotShadow ++ doNotDec ++ sourceZoneInfo.outputs, doNotDec)
//   }
//
//   // emit each zone
//   zonesReordered map { zoneName => (zoneName, zoneMap(zoneName)) } foreach {
//       case (zoneName, Graph.ZoneInfo(inputs, members, outputs)) => {
//     val sensitivityListStr = renameAndUnique(inputs, flagRenames) map genFlagName mkString(" || ")
//     if (sensitivityListStr.isEmpty)
//       writeLines(1, s"{")
//     else
//       writeLines(1, s"if ($sensitivityListStr) {")
//     val outputsCleaned = (outputs.toSet intersect inputsToZones diff regNamesSet).toSeq
//     val outputTypes = outputsCleaned map {name => findResultType(heMap(name).stmt)}
//     val oldOutputs = outputsCleaned zip outputTypes map {case (name, tpe) => {
//       s"${genCppType(tpe)} $name$$old = $name;"
//     }}
//     writeLines(2, oldOutputs)
//     val zoneEdges = (members.toSet diff regNamesSet).toSeq map heMap
//     // FUTURE: shouldn't this be made into tail?
//     writeBody(2, zoneEdges, doNotShadow ++ doNotDec, doNotDec)
//     val flagOffs = (zoneToLastFlags.getOrElse(zoneName, Seq()) map {
//       flagName => s"${genFlagName(flagName, flagRenames)} = false;"
//     }).toSeq
//     writeLines(2, flagOffs)
//     val outputChangeDetections = outputsCleaned map {
//       name => s"${genFlagName(name, flagRenames)} |= $name != $name$$old;"
//     }
//     writeLines(2, outputChangeDetections)
//     writeLines(1, "}")
//   }}
//
//   // emit rest of body (without redeclaring)
//   // FUTURE: does this need to be made into tail?
//   writeBody(1, nonZoneEdges, doNotShadow, doNotDec)
//
//   val memWriteTrackerUpdates = memFlags map { flagName => {
//     val trackerName = s"WTRACK_${flagName.replace('.','$')}"
//     val condition = memEnablesAndMasks(flagName).mkString(" && ");
//     s"$trackerName = $condition;"
//   }}
//   writeLines(1, memWriteTrackerUpdates.toSeq)
//
//   // init flags (and then start filling)
//   // writeLines(1, allFlags map { sigName => s"${genFlagName(sigName)} = false;" })
//   val regChecks = inputRegs map {
//     regName => s"${genFlagName(regName, flagRenames)} |= $regName != $regName$$next;"
//   }
//   writeLines(1, regChecks)
//   Seq()
// }