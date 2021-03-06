/**
 *  Neo Coolcam Light Switch 2CH v1.0.1
 *  (Models: NAS-SC02ZU-2 / NAS-SC02ZE-2)
 *
 *  Author: 
 *    Kevin LaFramboise (krlaframboise)
 *
 *  URL to documentation:  https://community.smartthings.com/t/release-neo-coolcam-light-switch-2ch/147756
 *    
 *
 *  Changelog:
 *
 *    1.0.1 (03/14/2020)
 *      - Fixed bug with enum settings that was caused by a change ST made in the new mobile app.
 *
 *    1.0 (12/26/2018)
 *      - Initial Release
 *
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
metadata {
	definition (
		name: "Neo Coolcam Light Switch 2CH", 
		namespace: "krlaframboise", 
		author: "Kevin LaFramboise",
		vid:"generic-switch"
	) {
		capability "Actuator"
		capability "Switch"		
		capability "Light"
		capability "Configuration"
		capability "Refresh"
		capability "Health Check"
				
		attribute "lastCheckIn", "string"
		attribute "syncStatus", "string"
		
		fingerprint mfr: "0258", prod: "0003", model: "008B", deviceJoinName: "NEO Coolcam Light Switch 2CH"
		
		fingerprint mfr: "0258", prod: "0003", model: "108B", deviceJoinName: "NEO Coolcam Light Switch 2CH"  //EU
	}

	simulator { }
	
	tiles(scale: 2) {
		multiAttributeTile(name:"switch", type: "generic", width: 6, height: 4){
			tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
				attributeState "on", label: '${name}', action: "switch.off", icon: "st.switches.light.on", backgroundColor: "#00a0dc"
				attributeState "off", label: '${name}', action: "switch.on", icon: "st.switches.light.off", backgroundColor: "#ffffff"
			}
		}
		standardTile("refresh", "device.refresh", width: 2, height: 2) {
			state "refresh", label:'Refresh', action: "refresh"
		}		
		valueTile("syncStatus", "device.syncStatus", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "syncStatus", label:'${currentValue}'
		}
		standardTile("sync", "device.configure", width: 2, height: 2) {
			state "default", label: 'Sync', action: "configure"
		}	
		main "switch"
		details(["switch", "refresh", "syncStatus", "sync"])
	}
	
	preferences {
		configParams.each {
			getParamInput(it)
		}

		input "debugOutput", "bool", 
			title: "Enable Debug Logging", 
			defaultValue: true, 
			required: false
	}
}

private getParamInput(param) {
	input "configParam${param.num}", "enum",
		title: "${param.name}:",
		required: false,
		defaultValue: "${param.value}",
		options: param.options
}


def installed() {
	logDebug "installed()..."
	state.refreshConfig = true
	
	return refresh()
}


def updated() {	
	if (!isDuplicateCommand(state.lastUpdated, 3000)) {
		state.lastUpdated = new Date().time
		
		logDebug "updated()..."
		
		initialize()
		
		executeConfigure()
	}
}

private initialize() {
	if (!device.currentValue("checkInterval")) {
		def checkInInterval = (3 * 60 * 60)
		def checkInterval = ((checkInInterval * 3) + (5 * 60))
			
		def eventMap = getEventMap("checkInterval", checkInterval, false)
		eventMap.data = [protocol: "zwave", hubHardwareId: device.hub.hardwareID]
			
		sendEvent(eventMap)	
	}
	
	unschedule()
	runEvery3Hours(ping)
}


def configure() {
	logDebug "configure()..."
	
	state.refreshConfig = true
	
	if (!childDevices) {
		addChildDevice(
			"smartthings", 
			"Child Switch", 
			"${device.deviceNetworkId}-2", 
			null,
			[
				completedSetup: true,
				isComponent: false,
				label: "${device.displayName} 2"
			]
		)	
	}
	
	runIn(2, executeConfigure)	
}


def executeConfigure() {
	def cmds = []
	
	if (!device.currentValue("switch")) {
		cmds <<  switchBinaryGetCmd(1)
		cmds <<  switchBinaryGetCmd(2)
	}
	
	configParams.each { param ->
		def storedVal = getParamStoredValue(param.num)
		def paramVal = param.value
		
		if (state.refreshConfig || "${storedVal}" != "${paramVal}") {
			logDebug "Changing ${param.name}(#${param.num}) from ${storedVal} to ${paramVal}"
			cmds << configSetCmd(param, paramVal)
			cmds << configGetCmd(param)
		}
	}
	
	state.refreshConfig = false
	if (cmds) {
		sendCommands(cmds)
	}
}

private sendCommands(cmds, delay=500) {
	def actions = []
	cmds?.each {
		actions << new physicalgraph.device.HubAction(it)
	}
	sendHubCommand(actions, delay)
	return []
}


def ping() {
	logDebug "Pinging device because it has not checked in"
	return [versionGetCmd()]
}


def on() {
	logDebug "on()..."
	childOn(device.deviceNetworkId)
}

def childOn(dni) {
	logTrace "childOn($dni)"
	state.pendingSwitch = "on"
	
	def ep = getEndPointFromDNI(dni)
	sendCommands([
		switchBinarySetCmd(0xFF, ep),
		switchBinaryGetCmd(ep)
	], 100)
}


def off() {
	logDebug "off()..."	
	return childOff(device.deviceNetworkId)
}

def childOff(dni) {
	logTrace "childOff($dni)"
	state.pendingSwitch = "off"
	
	def ep = getEndPointFromDNI(dni)
	return sendCommands([
		switchBinarySetCmd(0x00, ep),
		switchBinaryGetCmd(ep)
	], 100)
}


def refresh() {
	logDebug "refresh()..."
	return delayBetween([
		switchBinaryGetCmd(1),
		switchBinaryGetCmd(2)
	], 500)
}

private versionGetCmd() {
	return zwave.versionV1.versionGet().format()
}

private switchBinaryGetCmd(ep) {
	return multiChannelCmdEncapCmd(zwave.switchBinaryV1.switchBinaryGet(), ep)
}

private switchBinarySetCmd(val, ep) {
	return multiChannelCmdEncapCmd(zwave.switchBinaryV1.switchBinarySet(switchValue: val), ep)
}

private multiChannelCmdEncapCmd(cmd, ep) {	
	return zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: ep).encapsulate(cmd).format()
}

private configSetCmd(param, value) {
	return zwave.configurationV1.configurationSet(parameterNumber: param.num, size: param.size, scaledConfigurationValue: value).format()
}

private configGetCmd(param) {
	return zwave.configurationV1.configurationGet(parameterNumber: param.num).format()
}


private getCommandClassVersions() {
	[
		0x20: 1,	// Basic
		0x25: 1,	// Switch Binary
		0x59: 1,	// AssociationGrpInfo
		0x5A: 1,	// DeviceResetLocally
		0x5E: 2,	// ZwaveplusInfo
		0x60: 3,	// Multi Channel (4)
		0x70: 1,	// Configuration
		0x72: 2,	// ManufacturerSpecific
		0x73: 1,	// Powerlevel
		0x85: 2,	// Association
		0x86: 1,	// Version (2)
		0x8E: 2		// Multi Channel Association
	]
}


def parse(String description) {	
	def result = []
	def cmd = zwave.parse(description, commandClassVersions)
	if (cmd) {
		result += zwaveEvent(cmd)		
	}
	else {
		log.warn "Unable to parse: $description"
	}
		
	if (!isDuplicateCommand(state.lastCheckInTime, 60000)) {
		state.lastCheckInTime = new Date().time
		sendEvent(getEventMap("lastCheckIn", convertToLocalTimeString(new Date()), false))
	}
	return result
}


def zwaveEvent(physicalgraph.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
	def encapsulatedCommand = cmd.encapsulatedCommand([0x31: 3])
	
	if (encapsulatedCommand) {
		return zwaveEvent(encapsulatedCommand, cmd.sourceEndPoint)
	}
	else {
		logDebug "Unable to get encapsulated command: $cmd"
		return []
	}
}


def zwaveEvent(physicalgraph.zwave.commands.configurationv1.ConfigurationReport cmd) {	
	logTrace "ConfigurationReport ${cmd}"
	
	updateSyncingStatus()
	runIn(4, refreshSyncStatus)
	
	def param = configParams.find { it.num == cmd.parameterNumber }
	if (param) {	
		def val = cmd.scaledConfigurationValue		
		logDebug "${param.name}(#${param.num}) = ${val}"
		setParamStoredValue(param.num, val)		
	}
	else {
		logDebug "Parameter #${cmd.parameterNumber} = ${cmd.scaledConfigurationValue}"
	}		
	return []
}


def zwaveEvent(physicalgraph.zwave.commands.versionv1.VersionReport cmd) {
	// Ignore report because it's ised by the ping command to verify the device is still online.
	return []
}


def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd, ep=null) {
	// Ignore report because it's randomly sent without being requested.
	return []
}


def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd, ep=null) {
	logTrace "SwitchBinaryReport: ${cmd}, EP: ${ep}"
		
	def eventVal = (cmd.value == 0xFF) ? "on" : "off"
	def map = getEventMap("switch", eventVal, null, "Switch is ${eventVal}")
	
	map.type = (state.pendingSwitch == eventVal) ? "digital" : "physical"
	state.pendingSwitch = null
	
	if (ep != 2) {
		sendEvent(map)
	}
	else if (childDevices.length) {
		childDevices[0].sendEvent(map)
	}
	return []
}


def zwaveEvent(physicalgraph.zwave.Command cmd) {
	logDebug "Unhandled zwaveEvent: $cmd"
	return []
}


private updateSyncingStatus() {
	if (device.currentValue("syncStatus") != "Syncing...") {
		sendEvent(getEventMap("syncStatus", "Syncing...", false))
	}
}

def refreshSyncStatus() {
	def changes = pendingChanges	
	sendEvent(name: "syncStatus", value: (changes ?  "${changes} Pending Changes" : "Synced"), displayed: false)
}

private getPendingChanges() {	
	return configParams.count { "${it.value}" != "${getParamStoredValue(it.num)}" }
}

private getParamStoredValue(paramNum) {
	return safeToInt(state["configVal${paramNum}"] , null)
}

private setParamStoredValue(paramNum, value) {
	state["configVal${paramNum}"] = value
}


// Configuration Parameters
private getConfigParams() {
	return [
		backlightEnabledParam,
		onOffLedIndicatorEnabledParam,
		onStatusSavedEnabledParam,
		rootDeviceMappedParam
	]
}

private getBacklightEnabledParam() {
	return getParam(1, "Backlight Enabled", 1, 1, enabledDisabledOptions)
}

private getOnOffLedIndicatorEnabledParam() {
	return getParam(2, "On/Off LED Indicator Enabled", 1, 1, enabledDisabledOptions)
}

private getOnStatusSavedEnabledParam() {
	return getParam(3, "On/Off Status Saved Enabled", 1, 1, enabledDisabledOptions)
}

private getRootDeviceMappedParam() {
	return getParam(4, "Root Device Mapped", 1, 1, rootDeviceMappedOptions)
}

private getParam(num, name, size, defaultVal, options=null, range=null) {
	def val = safeToInt((settings ? settings["configParam${num}"] : null), defaultVal) 
	
	def map = [num: num, name: name, size: size, value: val]
	if (options) {
		map.valueName = options?.find { k, v -> "${k}" == "${val}" }?.value
		map.options = setDefaultOption(options, defaultVal)
	}
	if (range) map.range = range
	
	return map
}

private setDefaultOption(options, defaultVal) {
	return options?.collectEntries { k, v ->
		if ("${k}" == "${defaultVal}") {
			v = "${v} [DEFAULT]"		
		}
		["$k": "$v"]
	}
}


// Setting Options
private getEnabledDisabledOptions() {
	 return [
		"0":"Disabled", 
		"1":"Enabled"
	]
}

private getRootDeviceMappedOptions() {
	return [
		"0": "No Endpoint",
		"1": "Endpoint 1",
		"2": "Endpoing 2",
		"3": "Both Endpoints"
	]
}


private getEndPointFromDNI(dni) {
	return (dni == device.deviceNetworkId) ? 1 : 2
}


private getEventMap(name, value, displayed=null, desc=null, unit=null) {	
	desc = desc ?: "${name} is ${value}"
	
	def eventMap = [
		name: name,
		value: value,
		displayed: (displayed == null ? ("${getAttrVal(name)}" != "${value}") : displayed),
		isStateChange: true
	]
	
	if (unit) {
		eventMap.unit = unit
		desc = "${desc} ${unit}"
	}
	
	if (desc && eventMap.displayed) {
		logDebug desc
		eventMap.descriptionText = "${device.displayName} - ${desc}"
	}
	else {
		logTrace "Creating Event: ${eventMap}"
	}
	return eventMap
}

private getAttrVal(attrName) {
	try {
		return device?.currentValue("${attrName}")
	}
	catch (ex) {
		logTrace "$ex"
		return null
	}
}

private safeToInt(val, defaultVal=0) {
	return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal
}

private convertToLocalTimeString(dt) {
	def timeZoneId = location?.timeZone?.ID
	if (timeZoneId) {
		return dt.format("MM/dd/yyyy hh:mm:ss a", TimeZone.getTimeZone(timeZoneId))
	}
	else {
		return "$dt"
	}	
}

private isDuplicateCommand(lastExecuted, allowedMil) {
	!lastExecuted ? false : (lastExecuted + allowedMil > new Date().time) 
}

private logDebug(msg) {
	if (settings?.debugOutput || settings?.debugOutput == null) {
		log.debug "$msg"
	}
}

private logTrace(msg) {
	// log.trace "$msg"
}