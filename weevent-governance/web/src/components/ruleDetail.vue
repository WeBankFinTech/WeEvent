<template>
  <div class='rule_detail rule'>
    <div class='step'>
      <!-- <p class='rule_title'>{{$t('ruleDetail.guideDetail')}}</p> -->
      <p class='rule_name' style='font-size:18px'>
        {{ruleItem.ruleName}}
        <el-button type='primary' size='mini' @click="createRule = !createRule">{{$t('common.edit')}}</el-button>
      </p>
      <p class='name'><span>{{$t('rule.dataType')}} :</span>{{ruleItem.payloadType === 1 ? 'JSON' : '' }}</p>
      <p class='name'><span>{{$t('rule.payloadMap')}} :</span>{{ruleItem.payloadMap}}</p>
      <p class='name'><span>{{$t('rule.ruleDescription')}} :</span>{{ruleItem.ruleDescription}}</p>
    </div>
    <el-dialog :title="$t('ruleDetail.editRule')" :visible.sync="createRule" :close-on-click-modal='false'>
      <div class='warning_part'>
        <img src="../assets/image/icon_tips.svg" alt="">
        <p>{{$t('rule.creatRuleRemark')}}</p>
      </div>
      <el-form :model="rule" :rules="rules" ref='rule'>
        <el-form-item :label="$t('rule.ruleName')  + ' :'" prop='ruleName'>
          <el-input v-model="rule.ruleName" size='small' autocomplete="off"></el-input>
        </el-form-item>
        <el-form-item :label="$t('rule.dataType')  + ' :'">
          <el-radio-group v-model="rule.payloadType">
            <el-radio label="1">JSON</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="$t('rule.payloadMap')  + ' :'" prop='payloadMap'>
          <el-input v-model="rule.payloadMap" size='small' type='textarea' :rows='4' :placeholder="$t('rule.enterPayload')" autocomplete="off"></el-input>
        </el-form-item>
        <el-form-item :label="$t('rule.ruleDescription')  + ' :'">
          <el-input v-model="rule.ruleDescription" size='small' type='textarea' :rows='3' :placeholder="$t('rule.enterPayload')" autocomplete="off"></el-input>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" size="small" @click='update("rule")'>{{$t('rule.commit')}}</el-button>
        <el-button  size="small" @click="createRule = !createRule">{{$t('common.cancel')}}</el-button>
      </div>
    </el-dialog>

    <div class='step'>
      <p class='rule_title'>{{$t('ruleDetail.processData')}}</p>
      <el-button type='primary' size='mini' @click="createSQL = !createSQL">{{$t('rule.editRule')}}</el-button>
      <div class='sql_content'>
        <div class='no_sql' v-show='!fullSQL'>
          <img src="../assets/image/icon_tips.svg" alt="">
          <span>{{$t('ruleDetail.noRule')}}</span>
          <span class='creat_sql' @click="createSQL = !createSQL">{{$t('rule.editRule')}}</span>
        </div>
        <div class='sql_lession' v-show='fullSQL'>
          {{this.fullSQL}}
        </div>
      </div>
    </div>
    <el-dialog :title="$t('rule.editRule')" :visible.sync="createSQL" :close-on-click-modal='false'>
      <el-form :model="sqlOption" :rules="sqlCheck" ref='sql'>
        <el-form-item :label="$t('ruleDetail.dataCirculat')  + ' :'" prop='fromDestination'>
          <el-select  v-model='sqlOption.fromDestination'  size='mini' @visible-change='selectShow' :placeholder="$t('common.choose')">
            <el-option v-for='(item, index) in listTopic' :key='index' :label="item.topicName === '#' ? 'all': item.topicName" :value="item.topicName"></el-option>
            <el-pagination
                layout="prev, pager, next"
                small
                :current-page.sync="pageIndex"
                :total="total">
              </el-pagination>
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('ruleDetail.condition') + ' :'">
          <i class='des_icon el-icon-question' @click='showRuleDes = true'></i>
          <div style='text-align:right'>
            <span class='el-icon-plus' @click='addConditionItem'></span>
          </div>
          <tree :treeData='sqlOption.conditionFieldJson' :nodeIndex='"0"' :columnList='columnName'></tree>
        </el-form-item>
        <el-form-item :label="$t('ruleDetail.letter')  + ' :'"  :placeholder="$t('common.choose')" prop="selectField">
          <el-select v-model="sqlOption.selectField" size='small' :placeholder="$t('common.choose')" multiple @change="selField">
            <div v-show='JSON.stringify(columnName) !== "{}"' class='selAll'>
              <el-checkbox v-model="selAll" @change='selChange'>{{$t('common.all')}}</el-checkbox>
            </div>
            <el-option :label="key" :value="key" v-for='(item, key, index) in columnName' :key='index'></el-option>
            <el-option label="eventId" value="eventId" v-show='JSON.stringify(columnName) !== "{}"'></el-option>
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('ruleDetail.selectOperation')  + ' :'" prop='conditionType'>
          <el-select  v-model='sqlOption.conditionType' :placeholder="$t('ruleDetail.selectGuide')" size='mini' @change="selectType">
            <el-option :label="$t('ruleDetail.toTopic')" value="1"></el-option>
            <el-option :label="$t('ruleDetail.toDB')" value="2"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="Topic :" v-show="sqlOption.conditionType === '1'" prop='toDestination'>
          <el-select  v-model="sqlOption.toDestination" :placeholder="$t('ruleDetail.errorTopic')" size='mini' @visible-change='selectShow'>
            <el-option v-for='(item, index) in listData' :key='index' :label="item.topicName" :value="item.topicName"></el-option>
            <el-pagination
                layout="prev, pager, next"
                small
                :current-page.sync="pageIndex"
                :total="total">
            </el-pagination>
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('ruleDetail.db')  + ' :'" v-show="sqlOption.conditionType === '2'" prop='ruleDataBaseId' >
          <el-select  :placeholder="$t('ruleDetail.selectDB')" size='mini' name='options_dialog' v-model="sqlOption.ruleDataBaseId" v-show="dbList.length > 0">
            <el-option v-for="(item, index) in dbList" :key='index' :value="item.id" :label="item.datasourceName"></el-option>
          </el-select>
          <p class='no_dbList' v-show="dbList.length === 0">{{$t('ruleDetail.guideURL')}} <span @click="creatDB" >{{$t('ruleDetail.setGuide')}}</span></p>
        </el-form-item>
        <el-form-item :label="$t('rule.tableName')" v-show="sqlOption.ruleDataBaseId && sqlOption.conditionType === '2'" prop='tableName'>
          <el-input v-model.trim='sqlOption.tableName'  autocomplete="off" :placeholder="$t('rule.inputTableName')"></el-input>
        </el-form-item>
        <el-form-item :label="$t('ruleDetail.abnormalData')  + ' :'" prop='errorDestination'>
          <el-select  v-model="sqlOption.errorDestination" size='mini' :clearable="true" @visible-change='selectShow' :placeholder="$t('common.choose')">
            <el-option v-for='(item, index) in listData' :key='index' :label="item.topicName" :value="item.topicName"></el-option>
            <el-pagination
                layout="prev, pager, next"
                small
                :current-page.sync="pageIndex"
                :total="total">
            </el-pagination>
          </el-select>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" size="small" @click='update("sql")'>{{$t('rule.commit')}}</el-button>
        <el-button  size="small" @click="createSQL = !createSQL">{{$t('common.cancel')}}</el-button>
      </div>
    </el-dialog>
    <el-dialog :title="$t('ruleDes.conditionDes')" :visible.sync="showRuleDes" :fullscreen='true' :close-on-press-escape='true'>
      <ruleDes></ruleDes>
    </el-dialog>
  </div>
</template>
<script>
import API from '../API/resource'
import tree from './tool/tree.vue'
import ruleDes from './ruleDes'
import { checkRule } from '../utils/checkRule'
import { checkLoad } from '../utils/checkLoad'
export default {
  data () {
    var ruleName = (rule, value, callback) => {
      if (value === '') {
        callback(new Error(this.$t('rule.enterRuleName')))
      } else {
        callback()
      }
    }
    var payloadMap = (rule, value, callback) => {
      if (value === '') {
        callback(new Error(this.$t('rule.enterPayload')))
      } else {
        if (typeof value === 'string') {
          try {
            var obj = JSON.parse(value)
            if (typeof obj === 'object' && obj) {
              if (this.fullSQL) {
                if (checkLoad(value, this.ruleItem.payloadMap, this.sqlOption.functionArray, this.sqlOption.selectField)) {
                  callback()
                } else {
                  callback(new Error(this.$t('rule.errorPayLoad')))
                }
              } else {
                callback()
              }
            } else {
              callback(new Error(this.$t('rule.errorType')))
            }
          } catch (e) {
            callback(new Error(this.$t('rule.errorType')))
          }
        }
      }
    }
    var errorDestination = (rule, value, callback) => {
      if (!value) {
        callback()
      } else {
        if (value === this.sqlOption.fromDestination) {
          callback(new Error(this.$t('ruleDetail.cannotSame')))
        } else {
          callback()
        }
      }
    }
    var toDestination = (rule, value, callback) => {
      if (!value) {
        callback(new Error(this.$t('common.choose')))
      } else {
        if (value === this.sqlOption.fromDestination) {
          callback(new Error(this.$t('ruleDetail.cannotSame')))
        } else {
          callback()
        }
      }
    }
    var tableName = (rule, value, callback) => {
      if (!this.sqlOption.ruleDataBaseId) {
        callback()
      } else {
        if (!value) {
          callback(new Error(this.$t('rule.enterTableName')))
        } else {
          const list = this.dbList
          const id = this.sqlOption.ruleDataBaseId
          list.forEach(e => {
            if (e.id === id) {
              const data = {
                datasourceName: e.datasourceName,
                databaseType: e.databaseType,
                username: e.username,
                password: e.password,
                databaseIp: e.databaseIp,
                databasePort: e.databasePort,
                databaseName: e.databaseName,
                optionalParameter: e.optionalParameter,
                checkType: 2,
                id: e.id,
                tableName: value
              }
              API.checkJDBC(data).then(res => {
                if (res.data.code === 0) {
                  callback()
                } else {
                  this.crrentTable = false
                  callback(new Error(this.$t('rule.errorTable')))
                }
              })
            }
          })
        }
      }
    }
    return {
      showRuleDes: false,
      selAll: false,
      createRule: false,
      createSQL: false,
      crrentTable: true,
      ruleItem: {
        id: '',
        brokerId: '',
        ruleName: '',
        payloadType: '1',
        payloadMap: '',
        selectField: '',
        fromDestination: '',
        toDestination: '',
        conditionField: '',
        conditionType: '',
        ruleDataBaseId: '',
        errorDestination: '',
        conditionFieldJson: [],
        functionArray: '',
        ruleDescription: '',
        tableName: ''
      },
      rule: {
        ruleName: '',
        payloadType: '1',
        payloadMap: '',
        ruleDescription: ''
      },
      rules: {
        ruleName: [
          { required: true, validator: ruleName, trigger: 'blur' }
        ],
        payloadMap: [
          { required: true, validator: payloadMap, trigger: 'blur' }
        ]
      },
      sqlOption: {
        selectField: [],
        fromDestination: '',
        toDestination: '',
        conditionFieldJson: [],
        conditionType: '',
        ruleDataBaseId: '',
        errorDestination: '',
        functionArray: '',
        conditionField: '',
        tableName: ''
      },
      sqlCheck: {
        fromDestination: [
          { required: true, message: this.$t('ruleDetail.guideAddress'), trigger: 'change' }
        ],
        conditionType: [
          { required: true, message: this.$t('common.choose'), trigger: 'blur' }
        ],
        selectField: [
          { required: true, message: this.$t('common.choose'), trigger: 'change' }
        ],
        ruleDataBaseId: [
          { required: true, message: this.$t('ruleDetail.guideURL'), trigger: 'change' }
        ],
        toDestination: [
          { required: true, validator: toDestination, trigger: 'change' }
        ],
        errorDestination: [
          { validator: errorDestination, trigger: 'change' }
        ],
        tableName: [
          { required: true, validator: tableName, trigger: 'blur' }
        ]
      },
      dbList: [],
      condition: '',
      columnName: {},
      fullSQL: '',
      warning: '',
      pageIndex: 1,
      total: 0,
      listData: [],
      listTopic: [],
      ruleLetter: [],
      remarkLettrt: '',
      functionList: [],
      functionArray: []
    }
  },
  components: { tree, ruleDes },
  computed: {
    brokerId () {
      return this.$store.state.brokerId
    },
    groupId () {
      return this.$store.state.groupId
    },
    lang () {
      return this.$store.state.lang
    }
  },
  watch: {
    createRule (nVal) {
      if (!nVal) {
        const vm = this
        vm.$nextTick(fun => {
          for (const key in vm.rule) {
            if (key === 'payloadMap') {
              vm.rule.payloadMap = ''
              vm.rule.payloadMap = JSON.stringify(vm.ruleItem.payloadMap)
            } else if (key === 'payloadType') {
              vm.rule[key] = vm.ruleItem.payloadType.toString()
            } else {
              vm.rule[key] = vm.ruleItem[key]
            }
          }
        })
        vm.$refs.rule.resetFields()
      }
    },
    createSQL (nVal) {
      if (!nVal) {
        const vm = this
        vm.crrentTable = true
        const nodes = document.getElementsByClassName('tree_content')
        if (nodes) {
          for (let i = 0; i < nodes.length; i++) {
            const war = nodes[i].childNodes[nodes[i].childNodes.length - 1]
            war.style.display = 'none'
          }
        }
        vm.$nextTick(fun => {
          for (const key in vm.sqlOption) {
            if (key === 'selectField') {
              if (vm.ruleItem.selectField) {
                vm.sqlOption.selectField = [].concat(vm.ruleItem.selectField.split(','))
                const listColumnName = []
                for (const key in vm.columnName) {
                  listColumnName.push(key)
                }
                if (vm.sqlOption.selectField.length === listColumnName.length + 1) {
                  vm.selAll = true
                } else {
                  vm.selAll = false
                }
              }
            } else {
              if (key === 'conditionFieldJson') {
                vm.sqlOption.conditionFieldJson = [].concat(vm.ruleItem.conditionFieldJson)
              } else {
                vm.sqlOption[key] = vm.ruleItem[key]
              }
            }
          }
        })
        vm.$refs.sql.resetFields()
      }
    },
    brokerId () {
      this.$store.commit('set_menu', [this.$t('sideBar.engine'), this.$t('sideBar.ruleMana')])
      this.$store.commit('set_active', '4-1')
      this.$router.push('./rule')
    },
    groupId () {
      this.$store.commit('set_menu', [this.$t('sideBar.engine'), this.$t('sideBar.ruleMana')])
      this.$store.commit('set_active', '4-1')
      this.$router.push('./rule')
    },
    pageIndex (nVal) {
      this.getLsitData()
    },
    lang () {
      this.sqlCheck.fromDestination[0].message = this.$t('ruleDetail.guideAddress')
      this.sqlCheck.conditionType[0].message = this.$t('common.choose')
      this.sqlCheck.selectField[0].message = this.$t('common.choose')
      this.sqlCheck.ruleDataBaseId[0].message = this.$t('ruleDetail.guideURL')
      this.sqlCheck.toDestination[0].message = this.$t('common.choose')
    }
  },
  methods: {
    checkSQLData (x, y, z) {
      let vm = this
      const index = z + y
      if (!x[y].children) {
        x[y].children = []
      }
      const item = x[y].children
      if (item && item.length > 0) {
        vm.checkSQLData(item, 0, index)
      } else {
        vm.getNode(index)
      }
    },
    getNode (e) {
      let vm = this
      const list = e.split('')
      if (list.length === 1) {
        const index = Number(list[0])
        vm.sqlLetter(vm.sqlOption.conditionFieldJson[index], true)
        if (vm.sqlOption.conditionFieldJson[index + 1]) {
          vm.checkSQLData(vm.sqlOption.conditionFieldJson, index + 1, '')
        }
      } else {
        let nodeItem = vm.sqlOption.conditionFieldJson
        for (let i = 0; i < list.length - 1; i++) {
          const index = Number(list[i])
          const mid = nodeItem[index].children
          nodeItem = mid
        }
        const last = Number(list.pop())
        const newIndex = list.join('')
        vm.sqlLetter(nodeItem[last], false)
        if (nodeItem[last + 1]) {
          vm.checkSQLData(nodeItem, last + 1, newIndex)
        } else {
          vm.getNode(newIndex)
        }
      }
    },
    sqlLetter (item, e) {
      let vm = this
      let str
      if (item.functionType) {
        if (['abs', 'ceil', 'floor', 'round', 'lcase'].indexOf(item.functionType) !== -1) {
          str = item.functionType + '(' + item.columnName + ')' + item.conditionalOperator + item.sqlCondition
        }
        if (item.functionType === 'substring' || item.functionType === 'concat') {
          str = item.columnName + '.' + item.functionType + '(' + item.columnMark + ')' + item.conditionalOperator + item.sqlCondition
        }
        if (item.functionType === 'trim') {
          str = item.columnName + '.trim()' + item.conditionalOperator + item.sqlCondition
        }
        if (item.functionType === 'now' || item.functionType === 'currentDate' || item.functionType === 'currentTime') {
          str = item.columnName + item.conditionalOperator + item.functionType
        }
      } else {
        str = item.columnName + item.conditionalOperator + item.sqlCondition
      }
      if (item.children.length > 0) {
        if (item.connectionOperator) {
          str = ' ' + item.connectionOperator + ' ' + '(' + str + vm.remarkLettrt + ')'
        } else {
          str = '(' + str + vm.remarkLettrt + ')'
        }
        vm.remarkLettrt = str
      } else {
        if (item.connectionOperator) {
          str = ' ' + item.connectionOperator + ' ' + str
        }
        vm.remarkLettrt += str
      }
      if (e) {
        vm.ruleLetter.push(vm.remarkLettrt)
        vm.remarkLettrt = ''
      }
      if (item.functionType && vm.functionList.indexOf(item.functionType) === -1) {
        vm.functionList.push(item.functionType)
      }
    },
    getRuleData () {
      let vm = this
      let str = vm.ruleLetter.join('')
      const Js = vm.sqlOption.conditionFieldJson
      if (Js.length === 1 && Js[0].children.length > 0) {
        str = str.substr(1)
        str = str.substr(0, str.length - 1)
      }
      // 在这处理
      vm.sqlOption.conditionField = str
      vm.functionList.forEach(e => {
        let index = 0
        index = str.indexOf(e, 0)
        while (index !== -1) {
          const i = index + 1
          const startIndex = index + e.length
          const item = []
          if (['abs', 'ceil', 'floor', 'round', 'lcase'].indexOf(e) !== -1) {
            let endIndex = index + e.length
            while (str[endIndex] !== ')') {
              endIndex++
            }
            item[0] = String(index)
            item[1] = String(endIndex + 1)
            item[2] = e
            item[3] = str.substring(startIndex + 1, endIndex)
          }
          if (e === 'substring' || e === 'concat') {
            let endIndex = index
            while (str[endIndex] !== ' ' && str[endIndex] !== '(' && str[endIndex]) {
              endIndex--
            }
            let lastIndex = index + e.length
            while (str[lastIndex] !== ')') {
              lastIndex++
            }
            item[0] = String(endIndex + 1)
            item[1] = String(lastIndex + 1)
            item[2] = e
            const str1 = str
            const str2 = str
            const key = str1.substring(endIndex + 1, index - 1)
            const string = str2.substring(startIndex + 1, lastIndex)
            item[3] = key + ',' + string
          }
          if (e === 'trim') {
            let endIndex = index
            while (str[endIndex] !== ' ' && str[endIndex] !== '(' && str[endIndex]) {
              endIndex--
            }
            item[0] = String(endIndex + 1)
            item[1] = String(startIndex + 2)
            item[2] = e
            item[3] = str.substring(endIndex + 1, index - 1)
          }
          if (e === 'now' || e === 'currentDate' || e === 'currentTime') {
            item[0] = String(index)
            item[1] = String(index + e.length)
            item[2] = e
            let endIndex = index - 1
            while (['>', '<', '=', '!'].indexOf(str[endIndex]) !== -1) {
              endIndex--
            }
            let start = index - 1
            while (str[start] !== ' ' && str[start] !== '(' && str[start]) {
              start--
            }
            item[3] = str.substring(start + 1, endIndex + 1)
          }
          vm.functionArray.push(item)
          index = str.indexOf(e, i)
        }
      })
    },
    getDetail () {
      let vm = this
      const data = {
        brokerId: localStorage.getItem('brokerId'),
        id: sessionStorage.getItem('ruleId')
      }
      API.ruleDetail(data).then(res => {
        if (res.data.code === 0) {
          for (const key in vm.ruleItem) {
            if (res.data.data[key] || key === 'ruleDescription') {
              vm.ruleItem[key] = res.data.data[key]
              if (key === 'conditionFieldJson') {
                if (res.data.data.conditionFieldJson) {
                  vm.ruleItem.conditionFieldJson = JSON.parse(res.data.data.conditionFieldJson)
                } else {
                  vm.ruleItem.conditionFieldJson = []
                }
              }
            }
          }
          for (const key in vm.rule) {
            if (key === 'payloadMap') {
              vm.rule[key] = JSON.stringify(res.data.data.payloadMap)
            } else if (key === 'payloadType') {
              vm.rule[key] = res.data.data.payloadType.toString()
            } else {
              vm.rule[key] = res.data.data[key]
            }
          }
          for (const key in vm.sqlOption) {
            if (key === 'selectField' || key === 'conditionType' || key === 'conditionFieldJson') {
              if (res.data.data.selectField) {
                vm.sqlOption.selectField = [].concat(res.data.data.selectField.split(','))
              }
              if (key === 'conditionType') {
                if (res.data.data.conditionType || res.data.data.conditionType === 0) {
                  vm.sqlOption.conditionType = res.data.data.conditionType.toString()
                }
              }
              if (key === 'conditionFieldJson') {
                if (res.data.data.conditionFieldJson) {
                  vm.sqlOption.conditionFieldJson = JSON.parse(res.data.data.conditionFieldJson)
                } else {
                  vm.sqlOption.conditionFieldJson = []
                }
              }
            } else {
              vm.sqlOption[key] = res.data.data[key]
            }
          }
          if (vm.sqlOption.conditionType === '1') {
            vm.condition = vm.sqlOption.toDestination
          } else {
            vm.condition = vm.sqlOption.ruleDataBaseId
          }
          vm.fullSQL = res.data.data.fullSQL
          vm.columnName = Object.assign({}, JSON.parse(res.data.data.payload))
          const listColumnName = []
          for (const key in vm.columnName) {
            listColumnName.push(key)
          }
          if (vm.sqlOption.selectField.length === listColumnName.length + 1) {
            vm.selAll = true
          } else {
            vm.selAll = false
          }
        }
      })
    },
    getDBLsit () {
      API.dbList({}).then(res => {
        if (res.data.code === 0) {
          this.dbList = [].concat(res.data.data)
        }
      })
    },
    selectType (e) {
      let vm = this
      vm.$refs.sql.clearValidate('conditionType')
      if (e === '1') {
        vm.$refs.sql.clearValidate('toDestination')
        vm.sqlOption.ruleDataBaseId = this.ruleItem.ruleDataBaseId
        vm.sqlOption.tableName = this.ruleItem.tableName
      } else {
        vm.$refs.sql.clearValidate('ruleDataBaseId')
        vm.sqlOption.toDestination = this.ruleItem.toDestination
      }
    },
    creatDB () {
      this.$store.commit('set_menu', [this.$t('sideBar.engine'), this.$t('sideBar.sources')])
      this.$store.commit('set_active', '4-2')
      this.$router.push('./dataBase')
    },
    update (e) {
      let vm = this
      const data = Object.assign({}, vm.ruleItem)
      vm.$refs[e].validate((valid) => {
        if (!valid) {
          if (e === 'sql') {
            if (!vm.sqlOption.fromDestination) {
              return
            }
            if (vm.sqlOption.selectField.length === 0) {
              return
            }
            if (vm.sqlOption.conditionType === '1') {
              if (!vm.sqlOption.toDestination) {
                return
              } else {
                vm.$refs.sql.clearValidate('ruleDataBaseId')
                vm.sqlOption.ruleDataBaseId = ''
                vm.sqlOption.tableName = ''
              }
            } else {
              if (!vm.sqlOption.ruleDataBaseId) {
                return
              } else {
                vm.$refs.sql.clearValidate('toDestination')
                vm.sqlOption.toDestination = ''
                if (!vm.sqlOption.tableName) {
                  return
                } else {
                  if (!vm.crrentTable) {
                    return
                  }
                }
              }
            }
          } else {
            return
          }
        }
        if (e === 'rule') {
          for (let key in vm.rule) {
            data[key] = vm.rule[key]
            if (key === 'payloadMap') {
              data.payloadMap = JSON.parse(vm.rule.payloadMap)
            }
          }
          data.conditionFieldJson = JSON.stringify(vm.sqlOption.conditionFieldJson)
        }
        if (e === 'sql') {
          if (!checkRule(this.columnName, this.rule.payloadMap)) {
            return
          }
          vm.ruleLetter = []
          vm.functionArray = []
          vm.sqlOption.conditionField = ''
          if (vm.sqlOption.conditionFieldJson.length > 0) {
            vm.checkSQLData(vm.sqlOption.conditionFieldJson, 0, '')
            vm.getRuleData()
            vm.sqlOption.functionArray = JSON.stringify(vm.functionArray)
          } else {
            vm.sqlOption.functionArray = '[]'
          }
          if (vm.sqlOption.conditionType === '1') {
            vm.sqlOption.ruleDataBaseId = ''
            vm.sqlOption.tableName = ''
          } else {
            vm.sqlOption.toDestination = ''
          }
          for (let key in vm.sqlOption) {
            if (key === 'selectField') {
              data.selectField = vm.sqlOption.selectField.join(',')
            } else if (key === 'conditionFieldJson') {
              data.conditionFieldJson = JSON.stringify(vm.sqlOption.conditionFieldJson)
            } else {
              data[key] = vm.sqlOption[key]
            }
          }
        }
        API.ruleUpdate(data).then(res => {
          if (res.data.code === 0) {
            vm.$message({
              type: 'success',
              message: vm.$t('common.editSuccess')
            })
            vm.getDetail()
            vm.createRule = false
            vm.createSQL = false
          } else {
            vm.$store.commit('set_Msg', vm.$message({
              type: 'warning',
              message: res.data.message,
              duration: 0,
              showClose: true
            }))
          }
        })
      })
    },
    addConditionItem () {
      const item = {
        connectionOperator: '',
        columnName: '',
        conditionalOperator: '',
        sqlCondition: '',
        functionType: '',
        columnMark: '',
        children: []
      }
      this.sqlOption.conditionFieldJson.push(item)
    },
    getLsitData () {
      const vm = this
      const data = {
        pageIndex: vm.pageIndex - 1,
        pageSize: 10,
        brokerId: Number(localStorage.getItem('brokerId')),
        groupId: Number(localStorage.getItem('groupId'))
      }
      API.topicList(data).then(res => {
        if (res.status === 200) {
          vm.total = res.data.data.total
          vm.listData = [].concat(res.data.data.topicInfoList)
          vm.listTopic = [].concat(res.data.data.topicInfoList)
        }
      })
    },
    selectShow (e) {
      if (e && this.pageIndex !== 1) {
        this.pageIndex = 1
        this.getLsitData()
      }
    },
    selField (e) {
      const list = []
      for (const key in this.columnName) {
        list.push(key)
      }
      if (e.length === list.length + 1) {
        this.selAll = true
      } else {
        this.selAll = false
      }
    },
    selChange (e) {
      this.sqlOption.selectField = []
      if (e) {
        for (const key in this.columnName) {
          this.sqlOption.selectField.push(key)
        }
        this.sqlOption.selectField.push('eventId')
      }
    }
  },
  mounted () {
    this.getDetail()
    this.getDBLsit()
    this.getLsitData()
  }
}
</script>
