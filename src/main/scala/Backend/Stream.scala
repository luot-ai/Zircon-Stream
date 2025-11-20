import chisel3._
import chisel3.util._
import ZirconConfig.Stream._
import ZirconConfig.Cache._
import ZirconConfig.EXEOp._
import ZirconConfig.FifoRole._

class SERFIO extends Bundle {
    val iterCnt = Input(UInt(32.W))
    val rdata1 = Output(UInt(32.W))
    val rdata2 = Output(UInt(32.W))
}

class SEWBIO extends Bundle {
    val wvalid = Input(Bool())
    val iterCnt = Input(UInt(32.W))
    val wdata  = Input(UInt(32.W))
}

class SEISIO extends Bundle {
    val isCalStream = Input(Vec(12,Bool()))
    val iterCnt = Input(Vec(12,UInt(32.W)))
    val ready  = Output(Vec(12, Bool()))
}

class SEPipelineIO extends Bundle {
    val op      = Input(UInt(stInstBits.W))
    val src1    = Input(UInt(32.W))
    val src2    = Input(UInt(32.W))
    val cfgState = Input(Vec(streamCfgBits,Bool()))
    val valid = Input(Bool())
    val busy  = Output(Bool())
}


class StreamEngineIO extends Bundle {
    val rf = Vec(3, new SERFIO)
    val wb = Vec(3, new SEWBIO)
    val is = new SEISIO
    val rdIter = Flipped(new SERdIterIO)
    val pp  = new SEPipelineIO
    val mem = new MemIO(false)
}

class StreamEngine extends Module {
    val io = IO(new StreamEngineIO)
    

    val iCntMap = RegInit(VecInit.fill(iterNum)(0.U(32.W)))    //i_id -> itercnt
    val streamMap = RegInit(VecInit.fill(streamNum)(0.U(iterBits.W))) //fifo_id -> i_id
    val addrCfg = RegInit(VecInit.fill(streamNum)(0.U(32.W))) //fifo_id -> addr
    val addrDyn = RegInit(VecInit.fill(streamNum)(0.U(32.W))) //fifo_id -> addr
    val stateCfg = RegInit(VecInit.fill(streamNum)(VecInit.fill(streamCfgBits)(false.B))) //fifo_id -> [doneCfg,isLoad,...]
    val readyMap = RegInit(VecInit.fill(streamNum)(VecInit.fill(fifoWord)(false.B)))  //fifo_id,itercnt -> ready
    val Fifo = RegInit(VecInit.fill(streamNum)(VecInit.fill(fifoWord)(0.U(32.W))))  //fifo_id,itercnt -> data

    val lengthMap = RegInit(VecInit.fill(streamNum)(0.U(16.W))) //fifo_id -> load length
    val burstCntMap = RegInit(VecInit.fill(streamNum)(0.U(16.W))) //fifo_id -> load cnt
    val outerIterMap = RegInit(VecInit.fill(streamNum)(0.U(16.W))) //fifo_id -> outer Iter
    val oIterCntMap = RegInit(VecInit.fill(streamNum)(0.U(16.W))) //fifo_id -> outer Iter cnt

    val ppBits = io.pp
    val op = ppBits.op
    val src1 = ppBits.src1
    val src2 = ppBits.src2
    val valid = io.pp.valid
    io.pp.busy := false.B

    val isCfgI = op === CFGI && valid
    val isStepI = op === STEPI && valid
    val isCfgStream = op === CFGSTREAM && valid
    val isCal = op === CALSTREAM && valid

    val iId = src1(iterBits-1,0)
    val addr = src1
    val cfgLength = src1(31,16)
    val outerIter = src1(15,0)
    val fifoId = VecInit(src1(streamBits*2-1, streamBits),src1(streamBits-1, 0),src2(streamBits-1, 0))//fifo_src_0 fifo_src_1 fifo_dst    

    //----------------- 1:CORE -------------------
    //config
    when(isCfgI){
        iCntMap(0) := 0.U
        streamMap(fifoId(Dst)) := 0.U
        lengthMap(fifoId(Dst)) := cfgLength / l2LineWord.U
        outerIterMap(fifoId(Dst)) := outerIter
    }
    when(isCfgStream){
        addrCfg(fifoId(Dst)) := addr 
        addrDyn(fifoId(Dst)) := addr 
        stateCfg(fifoId(Dst)) := ppBits.cfgState
    }

    // dispatch stage
    val fireNum = PopCount(io.rdIter.fireStream)
    iCntMap(0) := iCntMap(0) + fireNum
    io.rdIter.iterCnt := iCntMap(0)

    // Issue stage
    for (i <- 0 until 12) {
        val isWordIdx = (io.is.iterCnt(i) % fifoWord.U) (log2Ceil(fifoWord)-1,0)
        io.is.ready(i) :=  io.is.isCalStream(i) & readyMap(0)(isWordIdx) & readyMap(1)(isWordIdx) & !readyMap(2)(isWordIdx)
    }

    // ReadOp stage + writeback stage
    for(i <- 0 until 3){
        val rfWordIdx = (io.rf(i).iterCnt % fifoWord.U) (log2Ceil(fifoWord)-1,0)
        io.rf(i).rdata1 := Fifo(0)(rfWordIdx)
        io.rf(i).rdata2 := Fifo(1)(rfWordIdx)
        when(io.wb(i).wvalid){
            val wbWordIdx = (io.wb(i).iterCnt % fifoWord.U) (log2Ceil(fifoWord)-1,0)
            Fifo(2)(wbWordIdx) := io.wb(i).wdata
            readyMap(0)(wbWordIdx) := false.B
            readyMap(1)(wbWordIdx) := false.B
            readyMap(2)(wbWordIdx) := true.B
        }
    }

    //----------------- 2:MEMORY -------------------
    val fifoSegNum = fifoWord / l2LineWord //FIFO大小= fifoSegNum * l2LineWord * 4B
    val axiLen = (l2LineBits / 32 - 1).U
    val axiSize = 2.U

    //----------------- 2.1:READ -------------------
    // fifoSegEmpty：Vec[streamNum,Vec(fifoSegNum,bool())]
    val fifoSegEmpty = VecInit.tabulate(streamNum){j=>
        VecInit.tabulate(fifoSegNum){k=>
            !readyMap(j).slice(k*l2LineWord, (k+1)*l2LineWord).reduce(_ || _)  &&  
            stateCfg(j)(LDSTRAEM) && stateCfg(j)(DONECFG)  && 
            (burstCntMap(j)(0)===k.U && oIterCntMap(j)=/=outerIterMap(j))
        }
    }
    //TODO 只使用两个load stream目前
    val fifo0Valid = fifoSegEmpty(0).asUInt.orR
    val fifo1Valid = fifoSegEmpty(1).asUInt.orR
    val loadValid  = fifo0Valid | fifo1Valid
    val loadFifoId = Mux(fifo1Valid && (burstCntMap(1)<burstCntMap(0)), 1.U ,0.U)
    val loadSegSel = Mux(fifo1Valid && (burstCntMap(1)<burstCntMap(0)), fifoSegEmpty(1).asUInt-1.U  , fifoSegEmpty(0).asUInt-1.U)
    
    // fetch from Mem
    val loadWordCnt     = RegInit(0.U((l2Offset-2).W)) // word cnt
    val loadValidReg      = RegInit(false.B)
    val loadFifoIdReg     = RegInit(0.U(streamBits.W))
    val loadSegSelReg     = RegInit(0.U(log2Ceil(fifoSegNum).W))
    when(io.mem.rreq && io.mem.rrsp){
        loadWordCnt := loadWordCnt + 1.U
    }
    when(!loadValidReg){
        loadValidReg := loadValid
        loadFifoIdReg := loadFifoId
        loadSegSelReg := loadSegSel
    }.elsewhen(io.mem.rreq && io.mem.rrsp && io.mem.rlast){
        loadValidReg := false.B
        val isWrap = (burstCntMap(loadFifoIdReg) + 1.U) % lengthMap(loadFifoIdReg) === 0.U
        when(isWrap)
        {
            oIterCntMap(loadFifoIdReg) :=oIterCntMap(loadFifoIdReg) + 1.U
        }
        addrDyn(loadFifoIdReg)     := Mux(isWrap, addrCfg(loadFifoIdReg), addrDyn(loadFifoIdReg) + l2Line.U)
        burstCntMap(loadFifoIdReg)  := burstCntMap(loadFifoIdReg) + 1.U
    }
    io.mem.rreq      := loadValidReg
    io.mem.raddr     := addrDyn(loadFifoIdReg)
    io.mem.rlen      := axiLen
    io.mem.rsize     := axiSize

    // refill FIFO
    val wFifoWen    = io.mem.rreq && io.mem.rrsp
    val wFifoData   = io.mem.rdata
    val wFifoIdx  = (loadSegSelReg * l2LineWord.U + loadWordCnt)(log2Ceil(fifoWord)-1,0) 
    when(wFifoWen) {
        Fifo(loadFifoIdReg)(wFifoIdx) := wFifoData
        readyMap(loadFifoIdReg)(wFifoIdx) := true.B
    }


    //----------------- 2.2:WRITE -------------------
    val storeFifoId = 2

    val wFifoSegFull = VecInit.tabulate(fifoSegNum){ k=> readyMap(storeFifoId).slice(k*l2LineWord, (k+1)*l2LineWord).reduce(_ && _) }
    val storeSegSel = PriorityEncoder(wFifoSegFull)
    val storeValid = stateCfg(storeFifoId)(DONECFG) && !stateCfg(storeFifoId)(LDSTRAEM) && wFifoSegFull.asUInt.orR
    
    val storeWordCnt     = RegInit(0.U((l2Offset-2).W)) // word cnt
    val storeValidReg      = RegInit(false.B)
    val storeSegSelReg     = RegInit(0.U(log2Ceil(fifoSegNum).W))
    val storeFifoIdx  = (storeSegSelReg * l2LineWord.U + storeWordCnt)(log2Ceil(fifoWord)-1,0) 
    when (io.mem.wreq.get && io.mem.wrsp.get){
        storeWordCnt := storeWordCnt + 1.U
        readyMap(storeFifoId)(storeFifoIdx):=false.B
    }
    when(!storeValidReg){
        storeValidReg  := storeValid
        storeSegSelReg := storeSegSel
    }.elsewhen(io.mem.wreq.get && io.mem.wrsp.get && io.mem.wlast.get){
        storeValidReg := false.B
        val isWrap = (burstCntMap(2) + 1.U) === lengthMap(2) 
        addrDyn(2)     := Mux(isWrap, addrCfg(2), addrDyn(2) + l2Line.U)
        burstCntMap(2) := Mux(isWrap, 0.U, burstCntMap(2) + 1.U)
        oIterCntMap(2) := Mux(isWrap, oIterCntMap(2) + 1.U, oIterCntMap(2))
    }
    // write Mem
    io.mem.wreq.get  := storeValidReg
    io.mem.waddr.get := addrDyn(storeFifoId)
    io.mem.wlen.get  := axiLen
    io.mem.wsize.get := axiSize
    io.mem.wstrb.get := 0xf.U
    io.mem.wlast.get := storeWordCnt.andR
    io.mem.wdata.get := Fifo(storeFifoId)(storeFifoIdx)
}