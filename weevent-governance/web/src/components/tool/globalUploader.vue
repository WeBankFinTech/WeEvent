<template>
  <div id="global-uploader">
    <uploader
      ref="uploader"
      :options="options"
      :autoStart="false"
      @file-added="onFileAdded"
      @file-success="onFileSuccess"
      @file-progress="onFileProgress"
      @file-error="onFileError"
      @file-removed='onRemoved'
      class="uploader-app">
      <uploader-unsupport></uploader-unsupport>
      <uploader-btn id="global-uploader-btn" ref="uploadBtn">file</uploader-btn>
      <uploader-list>
        <div class="file-panel" slot-scope="props" :class="{'collapse': collapse}">
          <div class="file-title">
            <h3>{{$t('file.uploadList')}}</h3>
          </div>
          <ul class="file-list">
            <li v-for="file in props.fileList" :key="file.id">
              <p class='file_topic_name' style='font-size:16px;color:#333;padding:5px;'>Topic: {{file.topicName}}</p>
              <uploader-file :class="'file_' + file.id" ref="files" :file="file" :list="true"></uploader-file>
            </li>
            <div class="no-file" v-if="!props.fileList.length">
            <i class="iconfont icon-empty-file"></i>{{$t('file.noFile')}}</div>
          </ul>
        </div>
      </uploader-list>
    </uploader>
  </div>
</template>
<script>
import Bus from './js/bus'
import SparkMD5 from 'spark-md5'
import $ from 'jquery'
import API from '../../API/resource'
const con = require('../../../config/config.js')
export default {
  data () {
    return {
      options: {
        // target: 'file/upload',
        target: con.ROOT + 'file/upload',
        // size = 1Mb
        chunkSize: '1048576',
        fileParameterName: 'upfile',
        maxChunkRetries: 3,
        // 是否开启服务器分片校验
        testChunks: true,
        forceChunkSize: true,
        checkChunkUploadedByResponse: function (chunk, message) {
          const objMessage = JSON.parse(message)
          if (objMessage.skipUpload) {
            return true
          }
          return (objMessage.data || []).indexOf(chunk.offset + 1) >= 0
        },
        headers: {
          Authorization: localStorage.getItem('token')
        },
        query () {}
      },
      collapse: false
    }
  },
  mounted () {
    Bus.$on('openUploader', query => {
      this.params = query || {}
      if (this.$refs.uploadBtn) {
        $('#global-uploader-btn').click()
      }
    })
  },
  computed: {
    uploader () {
      return this.$refs.uploader.uploader
    }
  },
  methods: {
    onFileAdded (file) {
      const vm = this
      file.topicName = sessionStorage.getItem('uploadName')
      const ov = sessionStorage.getItem('overWrite')
      if (ov === '0') {
        const url = '?fileName=' + file.name + '&brokerId=' + localStorage.getItem('brokerId') + '&groupId=' + localStorage.getItem('groupId') + '&topicName=' + sessionStorage.getItem('uploadName') + '&nodeAddress=' + sessionStorage.getItem('uploadNodeAddress') + '&role=' + sessionStorage.getItem('uploadRole')
        API.checkUploaded(url).then(res => {
          if (res.data.code === 0 && !res.data.data) {
            vm.computeMD5(file)
            Bus.$emit('fileAdded')
            vm.$emit('pop', true)
          } else {
            this.$store.commit('set_Msg', this.$message({
              type: 'warning',
              message: res.data.message,
              duration: 0,
              showClose: true
            }))
            this.$refs.uploader.files.pop()
            this.$refs.uploader.fileList.pop()
          }
        })
      } else {
       	vm.computeMD5(file)
        Bus.$emit('fileAdded')
        vm.$emit('pop', true)
      }
    },
    onFileProgress (rootFile, file, chunk) {
    },
    onFileSuccess (rootFile, file, response, chunk) {
      const res = JSON.parse(response)
      // 服务器自定义的错误（即虽返回200，但是是错误的情况），这种错误是Uploader无法拦截的
      if (res.code !== 0) {
        this.$message({ message: res.message, type: 'error' })
        // 文件状态设为“失败”
        this.statusSet(file.id, 'failed')
        return
      }
      // 如果服务端返回需要合并
      if (res.needMerge) {
        // 文件状态设为“合并中”
        this.statusSet(file.id, 'merging')
        // 不需要合并
      } else {
        Bus.$emit('fileSuccess')
      }
    },
    onFileError (rootFile, file, response, chunk) {
      this.$message({
        message: response,
        type: 'error'
      })
    },
    //  计算md5，实现断点续传及秒传
    computeMD5 (file) {
      const fileReader = new FileReader()
      const time = new Date().getTime()
      const blobSlice =
        File.prototype.slice ||
        File.prototype.mozSlice ||
        File.prototype.webkitSlice
      let currentChunk = 0
      const chunkSize = 1048576
      const chunks = Math.ceil(file.size / chunkSize)
      const spark = new SparkMD5.ArrayBuffer()
      // 文件状态设为"计算MD5"
      this.statusSet(file.id, 'md5')
      file.pause()
      loadNext()
      fileReader.onload = e => {
        spark.append(e.target.result)
        if (currentChunk < chunks) {
          currentChunk++
          loadNext()
          // 实时展示MD5的计算进度
          this.$nextTick(() => {
            $(`.myStatus_${file.id}`).text(
              'Check MD5 ' + (currentChunk / chunks * 100).toFixed(0) + '%'
            )
          })
        } else {
          const md5 = spark.end()
          this.computeMD5Success(md5, file)
          console.log(
            `MD5计算完毕：${file.name} \nMD5：${md5} \n分片：${chunks} 大小:${
              file.size
            } 用时：${new Date().getTime() - time} ms`
          )
        }
        this.$emit('addFile', this.$refs.uploader.files.length)
      }
      fileReader.onerror = function () {
        this.error(`文件${file.name}读取出错，请检查该文件`)
        file.cancel()
      }
      function loadNext () {
        const start = currentChunk * chunkSize
        const end = start + chunkSize >= file.size ? file.size : start + chunkSize
        fileReader.readAsArrayBuffer(blobSlice.call(file.file, start, end))
      }
    },
    computeMD5Success (md5, file) {
      // 将自定义参数直接加载uploader实例的opts上
      Object.assign(this.uploader.opts, {
        query: {
          ...this.params
        }
      })
      // console.log(this.uploader.opts)
      file.uniqueIdentifier = md5
      file.resume()
      this.statusRemove(file.id)
    },
    statusSet (id, status) {
      const statusMap = {
        md5: {
          text: 'check MD5',
          bgc: '#fff'
        },
        merging: {
          text: '',
          bgc: '#e2eeff'
        },
        transcoding: {
          text: 'changing',
          bgc: '#e2eeff'
        },
        failed: {
          text: 'fail',
          bgc: '#e2eeff'
        }
      }
      this.$nextTick(() => {
        $(`<p class="myStatus_${id}"></p>`)
          .appendTo(`.file_${id} .uploader-file-status`)
          .css({
            position: 'absolute',
            top: '0',
            left: '0',
            right: '0',
            bottom: '0',
            zIndex: '1',
            backgroundColor: statusMap[status].bgc
          })
          .text(statusMap[status].text)
      })
    },
    statusRemove (id) {
      this.$nextTick(() => {
        $(`.myStatus_${id}`).remove()
      })
    },
    error (msg) {
      this.$notify({
        title: 'error',
        message: msg,
        type: 'error',
        duration: 2000
      })
    },
    onRemoved (e) {
      this.$emit('addFile', this.$refs.uploader.files.length)
    }
  },
  destroyed () {
    Bus.$off('openUploader')
  }
}
</script>
