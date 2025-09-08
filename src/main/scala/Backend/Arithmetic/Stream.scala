import chisel3._
import chisel3.util._
import ZirconConfig.Stream._
import ZirconConfig.Cache._
import ZirconConfig.EXEOp._



class SEPipelineIO extends Bundle {
    val op      = Input(UInt(stInstBits.W))
    val src1    = Input(UInt(32.W))
    val src2    = Input(UInt(32.W))
}


class StreamEngineIO extends Bundle {
    val pp  = Decoupled(new SEPipelineIO)
    val mem = new MemIO(false)
}


class StreamEngine extends Module {
    val io = IO(new StreamEngineIO)
    

    val iCntMap = RegInit(VecInit.fill(iterNum)(0.U(32.W)))    //i_id -> itercnt
    val streamMap = RegInit(VecInit.fill(streamNum)(0.U(iterBits.W))) //fifo_id -> i_id
    val stateCfg = RegInit(VecInit.fill(streamNum)(0.U(33.W))) //fifo_id -> addr,doneCfg
    val readyMap = RegInit(VecInit.fill(streamNum)(false.B))  //fifo_id,itercnt -> ready
    val Fifo = RegInit(VecInit.fill(streamNum)(VecInit.fill(fifoLength)(0.U(32.W))))  //fifo_id,itercnt -> data

    val ppBits = io.pp.bits
    val op = ppBits.op
    val src1 = ppBits.src1
    val src2 = ppBits.src2
    val valid = io.pp.valid


    val isCfgI = op === CFGI && valid
    val isStepI = op === STEPI && valid
    val isCfgAddr = op === CFGADDR && valid
    val isCal = op === CALSTREAM && valid


    val iId = src1
    val addr = src1
    val stId = VecInit(src1(streamBits*2-1, streamBits),src1(streamBits-1, 0),src2(streamBits-1, 0))//fifo_src_0 fifo_src_1 fifo_dst
    val stIter = VecInit.tabulate(3){ j => streamMap(stId(j))}
    val stIndex   = VecInit.tabulate(3){ j => stIter(j) % fifoLength.U}

    //config
    val iMapWen = isCfgI || isStepI
    val iMapWdata = Mux(isCfgI , 0.U(32.W), iCntMap(iId) + 1.U(32.W))
    when(iMapWen){
        iCntMap(iId) := iMapWdata
    }
    when(isCfgI){
        streamMap(stId(2)) := iId
    }
    when(isCfgAddr){
        stateCfg(stId(2)) := addr ## 1.U(1.W)
    }

    //calculate
    val srcsRdy = VecInit.tabulate(2){ j=> readyMap(stId(j))(stIndex(j)) }
    val srcRdy = srcsRdy.asUInt.andR
    val datas = VecInit.tabulate(2){ j=> Fifo(stId(j))(stIndex(j)) }
    val res = datas(0) + datas(1)
    when(isCal){
        Fifo(stId(2))(stIndex(2)) := res
    }
    io.pp.ready := srcRdy


    //-----------------from AXI-------------------
    // val rbuf     = RegInit(0.U(l2LineBits.W))
    // when(io.mem.rreq && io.mem.rrsp){
    //     rbuf := io.mem.rdata ## rbuf(l2LineBits-1, 32)
    // }
    val rMemCnt     = RegInit(0.U((l2Offset-2).W))
    when(io.mem.rreq && io.mem.rrsp){
        rMemCnt := rMemCnt + 1.U
    }
    val wFifoId     = RegInit(0.U(streamBits.W))
    val wFifoWen    = io.mem.rreq && io.mem.rrsp
    val wFifoData   = io.mem.rdata
    // val wFifoIndex  = 

    //-----------------To AXI-------------------

    // io.mem.rreq      := fsmC2.io.mem.rreq
    // io.mem.raddr     := tag(c2s3.paddr) ## index(c2s3.paddr) ## Mux(c2s3.uncache, offset(c2s3.paddr), 0.U(l2Offset.W))
    // io.mem.rlen      := Mux(c2s3.uncache, 0.U, (l2LineBits / 32 - 1).U)
    // io.mem.rsize     := Mux(c2s3.uncache, c2s3.mtype, 2.U)
    // io.mem.wreq.get  := fsmC2.io.mem.wreq.get
    // io.mem.wlast.get := fsmC2.io.mem.wlast.get
    // io.mem.waddr.get := wbufC2.paddr(31, 2) ## 0.U(2.W)
    // io.mem.wdata.get := (wbufC2.wdata(31, 0) << (wbufC2.paddr(1, 0) << 3))
    // io.mem.wlen.get  := Mux(c2s3.uncache, 0.U, (l2LineBits / 32 - 1).U)
    // io.mem.wsize.get := 2.U
    // io.mem.wstrb.get := Mux(c2s3.uncache, mtype << wbufC2.paddr(1, 0), 0xf.U)
}