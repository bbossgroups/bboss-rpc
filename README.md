

# bboss rpc工程
 bboss rpc eclipse project.支持丰富的协议栈(http/netty/mina/jms/webservice/rmi/jgroups/restful) 安全高效,
 
   可非常方便地将bboss ioc管理的业务组件发布成RPC服务
# 工程gradle构建运行说明：
构建发布版本：gradle publish
对于cxf的使用说明，正式发布版本需要使用lib\cxf\cxf-core-3.1.0.jar替换war包中的cxf-core-3.1.0.jar

# 工程ant构建运行说明：
## 1.搭建好ant构建环境和jdk 1.7及以上
## 2.运行工程根目录下的build.bat指令
## 3.构建成功后,会生成两个文件：

distrib/bboss-rpc.jar

## License

The BBoss Framework is released under version 2.0 of the [Apache License][].

[Apache License]: http://www.apache.org/licenses/LICENSE-2.0
