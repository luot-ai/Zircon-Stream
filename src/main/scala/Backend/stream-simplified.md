opcode = 0x0b

fifo is 16bit

funct3区分指令：
need rs1
  000: step_i   id
  001：end_i    id
need rs1 rs2
  100：cfg_i    id,fifo
  101：cfg_addr addr,fifo
need rs1 rs2
  110：cal  [fifo，fifo] [fifo]


cfg i：
* iCntMap[i_id] = 0 
* streamMap[FIFO_id] = i_id

cfg addr:
* State[FIFO_id] = addr,ready
* ReadyMap[FIFO_id] = 64'b0 // 128Byte L2 = 32word ,we use 2 line

cal:时序不妙哦，但是先这样
* itercnt = iCntMap[i_id]  
  * readyMap[FIFO_id][itercnt] ready?
* op_x = FIFO[FIFO_id_x][itercnt]
* Exe(op_x,op_x,res)
* FIFO[FIFO_id][itercnt] = res

step i：
* iCntMap[i_id] ++


需要维护的推测状态：
iCntMap -> 恢复成commit icntMap
streamMap -> 恢复成commit streamMap
store流可能需要 commit flag


---

1. 这些指令的数据通路，放置的iq fu(其实还好，主要是cal指令)：新开
2. 预取器放置的位置，应该向 L1? L2? or AXI发出请求：AXI发起


取指：
译码/重命名：根据需要参加，同时func置为mul/div
派发->mdIQ：
发射->mdFU：
RO->SE:根据需要
Stream Engine内部可拆分多级，目前一级


新增4条指令：

cfg i(i_id)：使用rs1
cfg stream(fifo_id i_id)
step i(i_id)：
cal(fifo_id i_id)：

Q：streamIQ
FU：streamFU

icntMap
streamMap
Stream Engine：
    Defination(regs): FIFO_id -> { pattern, stride, width, length  }
      1. pattern (affine, indirect, linked) 3bit
      2. stride (4Byte) 2bit ?
      3. width (4Byte) 2bit ?
      4. length () bit?
    State(regs)：current address 32bit
    FIFO(regs)：FIFO_id -> .num x 32'b data
    ReadyMap(regs)：FIFO_id -> .num x 1'b ready


译码/重命名 不需要参加
DP到mdIQ
无阻塞顺序发射到mdFU
读操作数 不需要参加
执行级对3个东西操作一下

stream Engine的异步发请求：先直接发送到AXI？


