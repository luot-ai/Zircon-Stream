# V0-外挂模式

作为执行单元，单周期阻塞解决 判断是否就绪 / 读操作数+计算+写回操作数

## 文档说明结果
可参见[文档1](./流式访存优势.docx)和[文档2](./流式访存文档.docx)   

## 基本设计

1. 流的种类：
   定义了`iter流`和`访存流`。`iter流`对应程序中的循环计数`i`，`访存流`对应程序中对数据的访问；每一个流都由一个唯一的id标识

2. 流的访问模式：
   1. 线性访问：A[i] 
   
3. 流的依赖关系：
   仅实现 `iter流`->`访存流`的依赖关系，即A[i]的线性访问模式

4. 硬件扩展：
   1. iCntMap：每一个`iter流`对应1个值：当前循环迭代计数`i`
   2. streamMap：每一个`stream流`对应1个值：该`stream流`对应的`iter流`的id
   3. FIFO：每一个`stream流`对应 `X`项，每一项是一个`4byte`字  
      1. `X = 2 * L2Cache_line_WORD`
      2. TODO应实现较为复杂的分配策略:可指令配置，运行时调节
      3. 根据`stream_id`可以索引到对应的FIFO，根据`i`可以索引到FIFO中具体的项
   4. readyMap：每个FIFO的每一项对应1bit
   
5. 流的配置：
   1. `iter流`配置指令：cfg_i i_id(rs1), stream_id(rs2)：
      1. 从通用寄存器rs1 rs2中分别读出i_id和stream_id
      2. 初始化iCntMap[i_id]=0 
      3. streamMap[stream_id] = i_id
   2. `stream流`配置指令：cfg_stream: addr(rs1), stream_id(rs2)
      1. 从通用寄存器rs1 rs2中分别读出addr和stream_id
      2. 配置stream访存首地址：addrCfg[stream_id] = addr 
   3. ...

6. 流与主核的交互：
   1. 流计算指令：cal {stream_id_src0，stream_id_src1}(rs1)，stream_id_dest(rs2)：
      1. 从通用寄存器rs1 rs2中分别读出两个32bit数
         1. rs1对应的32bit：拼接了两个 `源stream_id`
         2. rs2对应的32bit：保存了 `目的stream_id`
      2. 从iCntMap中读出3个stream对应的`当前计数i`：iter=iCntMap[streamMap[stream_id]]
      3. 读出两个源操作数：src=FIFO[stream_id][iter % FIFO_length]
      4. 计算结果存入FIFO：FIFO[stream_id][index]=src cal src
   计算前 需要检查对应的load fifo index是否ready(`有数据可以计算`)，store fifo index是否!ready(`计算指令有空位写入`)
   计算后 将对应的load fifo index置为!ready(`可以向主存取数填入`)，store fifo index置为ready(`可以向主存存数`)
   这里假设每个index只会访问一次（因为是流？），读过之后置为空闲，腾出位置方便后续memory的加载

   2. 配置指令和流计算指令目前走muldiv流水线，在该流水线中实例化了一个Stream Engine(SE)用于处理指令(类似于mul srt模块)。目前所有指令在SE中都是顺序单周期执行

7. 流与存储系统的交互
   读
   1. select stream：选择一个已经配置好的，FIFO充裕的流读主存
      1. load流!ready(`可以向主存取数填入`)；
      2. 直连AXI Arbiter，与L2竞争AXI端口
   2. write Fifo：为FIFO维护一个offset，主存读数据从offset索引开始写FIFO：FIFO[stream_id][offset+cnt]，其中cnt是主存读字计数器，假设一次读32个字，则cnt=[0,31]
      1. 写入后置ready(`有数据可以计算`)
   写
   3. select stream：store流在满足条件下可以发送写请求
      1. store流ready(`可以向主存存数`)
   4. write Mem
      1. 完成写置!ready(`计算指令有空位写入`)

8. 流取数的细化
   1. 新增软件代码：配置每个流 `外循环次数` 以及 `内循环取数length`
   2. 新增硬件部分：维护每个流取的次数，当内循环计数达到配置次数时，取数地址绕回配置地址；当外循环计数达到配置次数，暂停取数

###  问题
1. 为什么需要`iter流`？`访存流`取回来的数据会放置在一个FIFO中，`iter`用作索引(其他实现方式包括“消耗性读”，即读完FIFO中的某项就弹出，自动读下一项，但这样无法处理FIFO中某项不需要使用的情况)
2. 配置指令为什么不一条指令全部配置好：确定了每个流所有要配置的信息之后 再作统一的修改  






## BUG
outer 20 times 偶尔会有bug


## 数据整理

### 64 word
custom：{Total cycles: 362, Total insts: 287}
normal：{Total cycles: 958, Total insts: 533}

### 512word
custom：{Total cycles: 1832, Total insts: 2080}
normal：{Total cycles: 6844, Total insts: 4120}
加速比 = 3.73

### 512word-20time 
![alt text](pic/image.png)  ![alt text](pic/image-1.png)
normal：{Total cycles: 128770, Total insts: 82077, IPC: 0.637392}
custom：{Total cycles: 33253, Total insts: 41071, IPC: 1.23511}
加速比 = 3.87