import BaseModel from './BaseModel'
import api from './api.json'
// change the servers of weBase tobe weevent-broker from 1.3 version
import blockChainAPI from './blockChainAPI'

class ResoruceService extends BaseModel {
  login (data) {
    return this.request(api.login, data)
  }

  loginOut (data) {
    return this.request(api.loginOut, data)
  }

  listGroup (data) {
    return this.request(api.listGroup, data)
  }

  getVersion (data) {
    return this.request(api.getVersion, data)
  }

  topicList (data) {
    return this.request(api.topicList, data)
  }
  
  openTopic (data) {
    return this.request(api.openTopic, data)
  }

  topicState (data) {
    return this.request(api.topicState, data)
  }

  topicInfo (data) {
    return this.request(api.topicInfo, data)
  }

  subscription (data) {
    return this.request(api.subscription, data)
  }

  addServer (data) {
    return this.request(api.addServer, data)
  }

  updateServer (data) {
    return this.request(api.updateServer, data)
  }

  getServer (data) {
    return this.request(api.getServer, data)
  }

  deleteServer (data) {
    return this.request(api.deleteServer, data)
  }

  getAll (data) {
    return this.request(blockChainAPI.getAll, data)
  }

  nodeList (data) {
    return this.request(blockChainAPI.nodeList, data)
  }

  general (data) {
    return this.request(blockChainAPI.general, data)
  }

  transDaily (data) {
    return this.request(blockChainAPI.transDaily, data)
  }

  transList (data) {
    return this.request(blockChainAPI.transList, data)
  }

  blockList (data) {
    return this.request(blockChainAPI.blockList, data)
  }

  blockByNumber (data) {
    return this.request(blockChainAPI.blockByNumber, data)
  }

  getEvent (data) {
    return this.request(blockChainAPI.getEvent, data)
  }

  checkExsit (data) {
    return this.request(api.checkExsit, data)
  }

  register (data) {
    return this.request(api.register, data)
  }

  forget (data) {
    return this.request(api.forget, data)
  }

  update (data) {
    return this.request(api.update, data)
  }

  reset (data) {
    return this.request(api.reset, data)
  }

  accountList (data) {
    return this.request(api.accountList, data)
  }

  permissionList (data) {
    return this.request(api.permissionList, data)
  }

  checkBrokerServer (data) {
    return this.request(api.checkBrokerServer, data)
  }

  ruleList (data) {
    return this.request(api.ruleList, data)
  }

  ruleAdd (data) {
    return this.request(api.ruleAdd, data)
  }

  ruleStart (data) {
    return this.request(api.ruleStart, data)
  }

  ruleUpdate (data) {
    return this.request(api.ruleUpdate, data)
  }

  ruleDelete (data) {
    return this.request(api.ruleDelete, data)
  }

  ruleStop (data) {
    return this.request(api.ruleStop, data)
  }

  ruleDetail (data) {
    return this.request(api.ruleDetail, data)
  }

  dbList (data) {
    return this.request(api.dbList, data)
  }

  dbDelete (data) {
    return this.request(api.dbDelete, data)
  }

  dbAdd (data) {
    return this.request(api.dbAdd, data)
  }

  dbUpdate (data) {
    return this.request(api.dbUpdate, data)
  }

  historicalData (data) {
    return this.request(api.historicalData, data)
  }

  getNodes (data) {
    return this.request(api.getNodes, data)
  }

  eventList (data) {
    return this.request(api.eventList, data)
  }

  checkJDBC (data) {
    return this.request(api.checkJDBC, data)
  }

  ruleStatic (data) {
    return this.request(api.ruleStatic, data)
  }

  timerList (data) {
    return this.request(api.timerList, data)
  }

  addTimer (data) {
    return this.request(api.addTimer, data)
  }

  updateTimer (data) {
    return this.request(api.updateTimer, data)
  }

  deleteTimer (data) {
    return this.request(api.deleteTimer, data)
  }

  cornCheck (data) {
    return this.request(api.cornCheck, data)
  }

  openTransport (data) {
    return this.request(api.openTransport, data)
  }

  deleteTransport (data) {
    return this.request(api.deleteTransport, data)
  }

  listTransport (data) {
    return this.request(api.listTransport, data)
  }
  
  getSubscribers (data) {
	return this.request(api.getSubscribers, data)
  }

  deleteStatus (data) {
    return this.request(api.deleteStatus, data)
  }

  listFile (data) {
    return this.request(api.listFile, data)
  }

  checkUploaded (data) {
    return this.request(api.checkUploaded, data)
  }

  downLoadStatus (data) {
    return this.request(api.downLoadStatus, data)
  }

  uploadStatus (data) {
    return this.request(api.uploadStatus, data)
  }

  checkUpload (data) {
    return this.request(api.checkUpload, data)
  }
}

export default new ResoruceService()
