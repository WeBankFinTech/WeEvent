<template>
<div class='event-table topic fileTranspoart'>
  <div class='refresh top_part'>
    <el-button type='primary' size='small' icon='el-icon-plus' @click='addNewOne'>{{$t('common.add')}}</el-button>
  </div>
  <el-table
    :data="tableData"
    v-loading='loading'
    element-loading-spinner='el-icon-loading'
    :element-loading-text="$t('common.loading')"
    element-loading-background='rgba(256,256,256,0.8)'
    style="width: 100%"
    @row-dblclick='rowClick'
    @expand-change='readDetail'
    ref='table'
    >
    <el-table-column type="expand">
      <template slot-scope="props">
        <h3>{{$t('file.fileuploadList')}}</h3>
        <el-table
          :data="props.row.detail"
          >
          <el-table-column
            label="File Name"
            prop="fileName"
          >
          </el-table-column>
          <el-table-column
            label="File Md5"
          >
            <template slot-scope="scope">
              <span :title='scope.row.fileMd5'>{{scope.row.fileMd5 ? scope.row.fileMd5 : '-'}}</span>
            </template>
          </el-table-column>
          <el-table-column
            label="File Size"
            prop="fileSize"
            :formatter="checkFileSize"
          >
          </el-table-column>
          <el-table-column
            label="Status"
            prop="status"
            :formatter="checkSta"
          >
          </el-table-column>
          <el-table-column
            label="Speed"
            prop="speed"
            :formatter="checkSpeed"
          >
          </el-table-column>
          <el-table-column
            label="Process"
            prop="process"
            :formatter="checkProcess"
          >
          </el-table-column>
        </el-table>
      </template>
    </el-table-column>
    <el-table-column
      label="Topic"
      prop="topicName"
      >
    </el-table-column>
    <el-table-column
      :label="$t('file.nodeAddress')"
      prop="nodeAddress"
    >
    </el-table-column>
    <el-table-column
      :label="$t('file.creater')"
      prop="creater"
    >
    </el-table-column>
     <el-table-column
      :label="$t('file.creatTime')"
      prop="createTime"
    >
    </el-table-column>
    <el-table-column
      :label="$t('file.roles')"
      prop="role"
      :formatter="checkRoles"
      >
    </el-table-column>
    <el-table-column
      :label="$t('file.certification')"
      >
      <template  slot-scope="scope">
        <el-tooltip v-show="scope.row.verified" class="item" effect="dark" :content="scope.row.role === '1'? scope.row.publicKey : scope.row.privateKey " placement="bottom">
          <span style='cursor: pointer'>{{$t('file.certified')}}</span>
        </el-tooltip>
        <span v-show="!scope.row.verified">{{$t('file.uncertified')}}</span>
      </template>
    </el-table-column>
    <el-table-column
      :label="$t('file.options')"
      width='300'
      >
      <template slot-scope="scope">
        <el-button size='mini' type='primary' @click.stop='fileOption(scope.row)' v-show="scope.row.role || scope.row.role === '1'">{{scope.row.role === '1' ? $t('file.upload') : $t('file.download')}}</el-button>
        <el-button size='mini' type='primary' @click='showlog = !showlog' @click.stop='getSubscribers(scope.row)' v-show="scope.row.role || scope.row.role === '1'">{{$t('file.subscribeList')}}</el-button>
        <el-button size='mini' type='primary' @click='generatePPK(scope.row)'>{{$t('file.generatePPK')}}</el-button>
        <el-tooltip v-show="scope.row.overWrite === '1'" class="item" effect="dark" :content="$t('file.fileCover')" placement="top">
          <i class='el-icon-warning' style='font-size:18px;color:#006cff'></i>
        </el-tooltip>
        <!-- <a class='el-icon-delete-solid' style='font-size:18px;margin-left:10px;cursor:pointer' @click.stop='deleteItem(scope.row)'></a> -->
      </template>
    </el-table-column>
  </el-table>
  <el-dialog :title="$t('file.addOne')" :visible.sync="dialogFormVisible" center width='450px' :close-on-click-modal='false'>
    <el-form :model="form" :rules="rules" ref='form' label-position="top">
      <el-form-item :label="$t('file.boundNode') + ':'" prop='node'>
        <el-select v-model="form.node">
          <el-option v-for="item in listNode" :value="item"></el-option>
        </el-select>
      </el-form-item >
      <el-form-item :label="$t('file.boundTopic') + ':'" prop='name'>
        <el-select v-model="form.name" @visible-change='selectShow'>
          <el-option v-for="(item, index) in listTopic" :key="index" :label="item.topicName" :value="item.topicName"></el-option>
          <el-pagination layout="prev, pager, next" small :current-page.sync="pageIndex" :total="total"></el-pagination>
        </el-select>
      </el-form-item >
      <el-form-item :label="$t('file.roles') + ':'" prop='roles'>
        <el-select v-model="form.roles">
          <el-option :label="$t('file.sender')" value="1"></el-option>
          <el-option :label="$t('file.receiver')" value="0"></el-option>
        </el-select>
      </el-form-item >
      <el-form-item :label="$t('file.publicKey') + ' :'" v-show='form.roles === "1"'>
        <el-input v-model="form.publicKey" type='textarea' :rows='3' autocomplete="off"></el-input>
      </el-form-item>
      <el-form-item :label="$t('file.privateKey') + ' :'" v-show='form.roles === "0"'>
        <el-input v-model="form.privateKey" type='textarea' :rows='3' autocomplete="off"></el-input>
      </el-form-item>
      <el-form-item :label="$t('file.overwrite')" v-show="form.roles === '1'">
        <el-radio-group v-model="form.overwrite">
          <el-radio label="1">Yes</el-radio>
          <el-radio label="0">No</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <div slot="footer" class="dialog-footer">
      <el-button type="primary" @click='addTopic(form)'>{{$t('common.ok')}}</el-button>
      <el-button @click="dialogFormVisible = false">{{$t('common.cancel')}}</el-button>
    </div>
  </el-dialog>

  <el-dialog :title="$t('file.subscribeList')" :visible.sync="showlog" center width='600px' :close-on-click-modal='false'>
    <el-table :data="topicTableData" v-loading='loading' element-loading-spinner='el-icon-loading' :element-loading-text="$t('common.loading')" element-loading-background='rgba(256,256,256,0.8)' style="width: 100%" @row-dblclick='rowClick' @expand-change='readDetail' ref='table'>
      <el-table-column :label="$t('file.nodeAddress')" prop="ipAndPort"></el-table-column>
    </el-table>
  </el-dialog>

  <el-dialog :title="$t('file.downloadList')" :visible.sync="showDownList" center :close-on-click-modal='false'>
    <el-table
    :data="downLoadList"
    style="width: 100%"
    >
      <el-table-column
        :label="$t('file.fileName')"
        prop="fileName"
      >
      </el-table-column>
      <el-table-column
        :label="$t('file.options')"
        width='150'
      >
        <template  slot-scope="scope">
          <el-button size='mini' type='primary' @click='dFile(scope.row)'>{{$t('file.downloadFile')}}</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-dialog>
 </div>
</template>
<script>
import API from '../API/resource.js'
import Bus from './tool/js/bus'
// import axios from 'axios'
const con = require('../../config/config.js')
export default {
  data () {
    var node = (node, value, callback) => {
      if (value === '') {
        callback(new Error(this.$t('tableCont.noName')))
      } else {
        callback()
      }
    }
    var name = (rule, value, callback) => {
      if (value === '') {
        callback(new Error(this.$t('tableCont.noName')))
      } else {
        callback()
      }
    }
    var roles = (rule, value, callback) => {
      if (value === '') {
        callback(new Error(this.$t('common.choose')))
      } else {
        callback()
      }
    }
    return {
      topicName: '',
      showDownList: false,
      loading: false,
      dialogFormVisible: false,
      showlog: false,
      tableData: [],
      topicTableData: [],
      fileList: [],
      listTopic: [],
      listNode: [],
      downLoadList: [],
      form: {
      	node: '',
        name: '',
        roles: '',
        overwrite: '0',
        publicKey: '',
        privateKey: ''
      },
      rules: {
        node: [
          { required: true, validator: node, trigger: 'blur' }
        ],
        name: [
          { required: true, validator: name, trigger: 'blur' }
        ],
        roles: [
          { required: true, validator: roles, trigger: 'change' }
        ]
      },
      downList: {},
      pageIndex: 0,
      total: 0
    }
  },
  methods: {
    checkName (e) {
      if (!e.topicName) {
        return '—'
      } else {
        return e.topicName
      }
    },
    checkRoles (e) {
      if (e.role === '1') {
        return this.$t('file.sender')
      } else if (e.role === '0') {
        return this.$t('file.receiver')
      } else {
        return '-'
      }
    },
    addNewOne () {
      this.dialogFormVisible = true
    },
    getData () {
      const vm = this
      vm.loading = true
      setTimeout(fun => {
        const url = '?brokerId=' + localStorage.getItem('brokerId') + '&groupId=' + localStorage.getItem('groupId')
        API.listTransport(url).then(res => {
          if (res.data.code === 0) {
            vm.tableData = [].concat(res.data.data)
            const det = []
            vm.tableData.forEach(item => {
              vm.$set(item, 'detail', det)
            })
          } else {
            vm.$store.commit('set_Msg', this.$message({
              type: 'warning',
              message: this.$t('tableCont.getDataError'),
              duration: 0,
              showClose: true
            }))
          }
          vm.loading = false
        }).catch(e => {
          vm.loading = false
        })
      }, 500)
    },
    addTopic (form) {
      const vm = this
      vm.$refs.form.validate((valid) => {
        if (valid) {
          const data = {
          	nodeAddress: vm.form.node,
            topicName: vm.form.name,
            brokerId: Number(localStorage.getItem('brokerId')),
            groupId: localStorage.getItem('groupId'),
            role: vm.form.roles,
            creater: localStorage.getItem('user'),
            overWrite: vm.form.overwrite,
            publicKey: '',
            privateKey: ''
          }
          if (data.role === '1') {
            data.publicKey = this.form.publicKey
            data.privateKey = ''
            this.$nextTick(fun => {
              this.form.privateKey = ''
            })
          } else {
            data.privateKey = this.form.privateKey
            data.publicKey = ''
            this.$nextTick(fun => {
              this.form.publicKey = ''
            })
          }
          API.openTransport(data).then(res => {
            if (res.data.code === 0) {
              vm.$message({
                type: 'success',
                message: this.$t('common.addSuccess')
              })
              vm.getData()
            } else {
              vm.$store.commit('set_Msg', vm.$message({
                type: 'warning',
                message: res.data.message,
                duration: 0,
                showClose: true
              }))
            }
            vm.dialogFormVisible = false
          }).catch(e => {
            vm.$store.commit('set_Msg', vm.$message({
              type: 'warning',
              message: this.$t('common.addFail'),
              duration: 0,
              showClose: true
            }))
          })
          vm.dialogFormVisible = false
        } else {
          return false
        }
      })
    },
    editItem (e) {
    },
    deleteItem (e) {
      const vm = this
      vm.$confirm(vm.$t('file.deleteTopic'), vm.$t('file.isDelete'), {
        confirmButtonText: vm.$t('common.ok'),
        cancelButtonText: vm.$t('common.cancel'),
        type: 'warning'
      }).then(() => {
        const data = {
          topicName: e.topicName,
          id: e.id,
          role: e.role,
          brokerId: Number(localStorage.getItem('brokerId')),
          groupId: localStorage.getItem('groupId')
        }
        API.deleteTransport(data).then(res => {
          if (res.data.code === 0) {
            vm.$message({
              type: 'success',
              message: vm.$t('rule.hasDelete')
            })
            vm.getData()
          } else {
            this.$store.commit('set_Msg', this.$message({
              type: 'warning',
              message: res.data.message,
              duration: 0,
              showClose: true
            }))
          }
        })
      }).catch(() => {})
    },
    rowClick (e) {
      this.$refs.table.toggleRowExpansion(e)
    },
    downStatus (e) {
      const vm = this
      const url = '?brokerId=' + localStorage.getItem('brokerId') + '&groupId=' + localStorage.getItem('groupId') + '&topicName=' + e.topicName + '&nodeAddress=' + e.nodeAddress
      API.downLoadStatus(url).then(res => {
        if (res.data.code === 0) {
          if (res.data.data && res.data.data.length > 0) {
            const list = res.data.data
            const newList = []
            list.forEach(item => {
              const e = {
                fileName: item.fileName,
                fileSize: item.fileSize,
                fileMd5: item.fileMd5,
                process: item.process,
                speed: item.speed,
                status: item.status
              }
              newList.push(e)
            })
            vm.$set(e, 'detail', newList)
          } else {
            const name = e.topicName
            window.clearInterval(vm.downLoadList[name])
          }
        } else {
          const name = e.topicName
          window.clearInterval(vm.downLoadList[name])
          vm.$store.commit('set_Msg', vm.$message({
            type: 'warning',
            message: res.data.message,
            duration: 0,
            showClose: true
          }))
        }
      }).catch(e => {
        const name = e.topicName
        window.clearInterval(vm.downLoadList[name])
      })
    },
    upStatus (e) {
      const vm = this
      const url = '?brokerId=' + localStorage.getItem('brokerId') + '&groupId=' + localStorage.getItem('groupId') + '&topicName=' + e.topicName + '&nodeAddress=' + e.nodeAddress
      API.uploadStatus(url).then(res => {
        if (res.data.code === 0) {
          if (res.data.data && res.data.data.length > 0) {
            const list = res.data.data
            const newList = []
            list.forEach(item => {
              const e = {
                fileName: item.fileName,
                fileSize: item.fileSize,
                fileMd5: item.fileMD5,
                process: item.process,
                speed: item.speed,
                status: item.status
              }
              newList.push(e)
            })
            vm.$set(e, 'detail', newList)
          } else {
            const name = e.topicName
            window.clearInterval(vm.downLoadList[name])
          }
        } else {
          const name = e.topicName
          window.clearInterval(vm.downLoadList[name])
          vm.$store.commit('set_Msg', vm.$message({
            type: 'warning',
            message: res.data.message,
            duration: 0,
            showClose: true
          }))
        }
      }).catch(e => {
        const name = e.topicName
        window.clearInterval(vm.downLoadList[name])
      })
    },
    readDetail (e) {
      const vm = this
      const name = e.topicName
      if (vm.downLoadList[name]) {
        window.clearInterval(vm.downLoadList[name])
        delete vm.downLoadList[name]
        vm.$set(e, 'detail', [])
      } else {
        if (e.role === '0') {
          vm.downLoadList[name] = setInterval(fun => {
            vm.downStatus(e)
          }, 1000)
        } else {
          vm.downLoadList[name] = setInterval(fun => {
            vm.upStatus(e)
          }, 1000)
        }
      }
    },
    checkFileSize (e) {
      const b = 1024
      const mb = 1024 * 1024
      const g = 1024 * 1024 * 1024
      const size = e.fileSize
      if (!size) {
        return '-'
      }
      if (size < mb) {
        return (size / b).toFixed(1) + ' KB'
      }
      if (size >= mb && size < g) {
        return (size / mb).toFixed(1) + ' MB'
      }
      if (size >= g) {
        return (size / g).toFixed(1) + ' G'
      }
    },
    checkSpeed (e) {
      const b = 1024
      const mb = 1024 * 1024
      if (!e.speed) {
        const k = (e.fileSize / b).toFixed(1)
        if(k > b){
      	  return (k / b).toFixed(1) + ' MB / s'
        }
        return k + ' KB / s'
      }
      const nu = e.speed.split('B/s')
      const size = Number(nu[0])
      if (size < b) {
        const v = (size / b).toFixed(1)
        if(v === '0.0') {
        	return (e.fileSize / b).toFixed(1) + ' KB / s'
        }
        return v + ' KB / s'
      }
      if (size >= b) {
        const m = (size / b).toFixed(1)
        if(m > b){
          return (m / b).toFixed(1) + ' MB / s'
        }
        return m + ' KB / s'
      }
    },
    checkProcess (e) {
      if (e.process) {
        return e.process
      } else {
        return '-'
      }
    },
    checkSta (e) {
      const s = e.status
      if (s === '0') {
        return 'uploading'
      } else if (s === '1') {
        return 'success'
      } else if (s === '2') {
        return 'faild'
      } else if (s === '3'){
      	return 'downloading'
      } else {
        return '-'
      }
    },
    fileOption (e) {
      if (e.role === '1') {
        // upload file
        sessionStorage.setItem('uploadName', e.topicName)
        sessionStorage.setItem('uploadNodeAddress', e.nodeAddress)
        sessionStorage.setItem('uploadRole', e.role)
        sessionStorage.setItem('overWrite', e.overWrite)
        Bus.$emit('openUploader', {
          nodeAddress: e.nodeAddress,
          role: e.role,
          topicName: e.topicName,
          brokerId: localStorage.getItem('brokerId'),
          groupId: localStorage.getItem('groupId'),
          nodeAddress: e.nodeAddress
        })
      } else {
        // download file
        const url = '?brokerId=' + localStorage.getItem('brokerId') + '&groupId=' + localStorage.getItem('groupId') + '&topicName=' + e.topicName + '&nodeAddress=' + e.nodeAddress + '&role=' + e.role
        API.listFile(url).then(res => {
          if (res.data.code === 0) {
            this.downLoadList = [].concat(res.data.data)
          } else {
            this.$store.commit('set_Msg', this.$message({
              type: 'warning',
              message: this.$t('tableCont.getDataError'),
              duration: 0,
              showClose: true
            }))
          }
        })
        this.showDownList = true
      }
    },
    chooseFile () {
      const input = document.createElement('input')
      input.attributes.type = 'file'
      input.focus()
    },
    selectShow (e) {
      if (e && this.pageIndex !== 1) {
        this.pageIndex = 1
        this.getListData()
      }
    },
    getListData () {
      const vm = this
      const data = {
        pageIndex: vm.pageIndex - 1,
        pageSize: 10,
        brokerId: Number(localStorage.getItem('brokerId')),
        groupId: Number(localStorage.getItem('groupId'))
      }
      API.topicList(data).then(res => {
        if (res.status === 200) {
          if (res.data.data.total) {
            vm.total = res.data.data.total
          }
          if (res.data.data.topicInfoList) {
            vm.listTopic = [].concat(res.data.data.topicInfoList)
          }
          if(res.data.data.nodeAddress){
            this.listNode = [].concat(res.data.data.nodeAddress)
          }
        }
      })
    },
    getSubscribers (e) {
      const topicName = e.topicName
      const nodeAddress = e.nodeAddress
      const role = e.role
      const data = {
        brokerId: Number(localStorage.getItem('brokerId')),
        groupId: Number(localStorage.getItem('groupId')),
        topicName: topicName,
        role: role,
        nodeAddress: nodeAddress
      }
      API.getSubscribers(data).then(res => {
        if (res.data.code === 0) {
          if (res.data.data) {
            this.topicTableData = [].concat(res.data.data)
          }
        }
      })
    },
    dFile (e) {
      const url = con.ROOT + 'file/download?topic=' + e.topic + '&fileName=' + e.fileName + '&groupId=' + e.groupId
      var xhr = new XMLHttpRequest()
      var formData = new FormData()
      xhr.open('get', url)
      xhr.setRequestHeader('Authorization', localStorage.getItem('token'))
      xhr.responseType = 'blob'
      xhr.onload = function (e) {
        if (this.status === 200) {
          const blob = this.response
          const f = this.getResponseHeader('filename')
          const filename = decodeURI(f)
          if (window.navigator.msSaveOrOpenBlob) {
            navigator.msSaveBlob(blob, filename)
          } else {
            var a = document.createElement('a')
            var url = window.URL.createObjectURL(blob)
            a.href = url
            a.download = filename
            document.body.appendChild(a)
            a.click()
            window.URL.revokeObjectURL(url)
          }
        }
      }
      xhr.send(formData)
    },
    generatePPK (e) {
      const vm = this
      const url = con.ROOT + 'file/genPemFile?brokerId=' + localStorage.getItem('brokerId') + '&groupId=' + localStorage.getItem('groupId') + '&topicName=' + e.topicName + '&nodeAddress=' + e.nodeAddress
      var xhr = new XMLHttpRequest()
      var formData = new FormData()
      xhr.open('get', url)
      xhr.setRequestHeader('Authorization', localStorage.getItem('token'))
      xhr.responseType = 'blob'
      xhr.onload = function (e) {
        if (this.status === 200) {
          const blob = this.response
          const f = this.getResponseHeader('filename')
          const filename = decodeURI(f)
          if("null" === filename){
          	vm.$message({
              type: 'warning',
              message: vm.$t('file.createFileTopic'),
              duration: 0,
              showClose: true
            })
          	return;
          }
          if (window.navigator.msSaveOrOpenBlob) {
            navigator.msSaveBlob(blob, filename)
          } else {
            var a = document.createElement('a')
            var url = window.URL.createObjectURL(blob)
            a.href = url
            a.download = filename
            document.body.appendChild(a)
            a.click()
            window.URL.revokeObjectURL(url)
          }
        }
      }
      xhr.send(formData)
    }
  },
  mounted () {
    // if the data is exit so it means click form subscribtion list
    this.getData()
  },
  computed: {
    brokerId () {
      return this.$store.state.brokerId
    },
    groupId () {
      return this.$store.state.groupId
    }
  },
  watch: {
    dialogFormVisible (nVal) {
      if (!nVal) {
      	this.form.node = ''
        this.form.name = ''
        this.form.roles = ''
        this.form.publicKey = ''
        this.form.privateKey = ''
        this.form.overwrite = '0'
        this.$refs.form.resetFields()
      }
    },
    pageIndex (nVal) {
      this.getListData()
    },
    groupId (nVal) {
      if (nVal !== '-1') {
      	this.getData()
      }
    },
    brokerId (nVal) {
      if (nVal !== '-1') {
      	this.getData()
      }
    }
  },
  beforeDestroy () {
    let vm = this
    for (const key in vm.downLoadList) {
      window.clearInterval(vm.downLoadList[key])
    }
  }
}
</script>
