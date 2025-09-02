cfg i：

* 译码 -> iCntMap[i_id] = 0 

cfg stream

* 译码 -> streamMap[FIFO_id] = i_id
* Exe: Definition，State，ReadyMap

step i：

* 译码 -> iCntMap[i_id] ++

cal:

* 译码 -> i_id = streamMap[FIFO_id] 
* DP -> itercnt = iCntMap[i_id]  【此处有数据相关，但step在译码阶段】
* 发射 -> FIFO[FIFO_id][itercnt] ready?
* 读寄存器 -> op = FIFO[FIFO_id][itercnt]
* Exe