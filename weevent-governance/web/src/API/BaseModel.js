import axios from 'axios'
import { Message } from 'element-ui'
import qs from 'qs'
import store from '../store'
const con = require('../../config/config.js')

class BaseModule {
  constructor () {
    this.$http = axios.create({
      timeout: 15 * 1000,
      withCredentials: true
    })

    this.dataMethodDefaults = {
      headers: {
        'Content-Type': 'application/json;charset=UTF-8',
        'X-Requested-With': 'XMLHttpRequest'
      }
    }
    // 请求前的数据拦截
    this.$http.interceptors.request.use(config => {
      config.url = con.ROOT + config.url
      if (config.method === 'delete' || config.method === 'get') {
        config.url = config.url + config.data
      }
      return config
    })
    // 收到数据的拦截
    this.$http.interceptors.response.use(config => {
      return new Promise((resolve, reject) => {
        let data = config.data
        let status = config.status
        if (((status >= 200 && status < 300) || status === 302) && data) {
          if (data.code === 302000) {
            store.commit('back', true)
          } else {
            resolve(config)
          }
        } else {
          Message({
            type: 'warning',
            message: '请求异常'
          })
          reject(config)
        }
      }).catch((e) => {
        Message({
          type: 'error',
          message: '请求未响应,稍后重试'
        })
      })
    }, error => {
      if (error.message.includes('timeout')) {
        Message({
          type: 'error',
          message: '请求超时请稍后重试'
        })
      } else {
        Message({
          type: 'error',
          message: '数据请求失败'
        })
      }
    })
  }

  request (config, data = undefined) {
    if (config.method && config.method.toLowerCase() === 'post') {
      if (config.headers) {
        return this.$http({
          url: config.url,
          method: 'post',
          data: qs.stringify(data),
          headers: config.headers
        })
      } else {
        return this.post(config.url, data, config)
      }
    } else if (config.method && config.method.toLowerCase() === 'put') {
      return this.put(config.url, data, config)
    } else if (config.method && config.method.toLowerCase() === 'delete') {
      return this.$http({
        url: config.url,
        method: 'delete',
        data: data
      })
    } else {
      return this.$http({
        url: config.url,
        method: 'get',
        data: data
      })
    }
  }

  get (url, config = {}) {
    return this.$http.get(url.config)
  }

  put (url, data = undefined, config = {}) {
    return this.$http.put(url, data, { ...this.dataMethodDefaults, ...config })
  }

  post (url, data = undefined, config = {}) {
    return this.$http.post(url, data, { ...this.dataMethodDefaults, ...config })
  }

  delete (url, config = {}) {
    return this.$http.delete(url.config)
  }
}

export default BaseModule
