## 1. stream engine 内部load流水线更新逻辑
```scala
/*
    若in.ready
        该级更新为上一级（若上一级out.valid）
        或0（若上一级不out.valid）
    否则若out.ready，则该级更新为0
    否则(in out都不ready)保持不变

    out.valid := valid && readyGo
    假设我们本级不valid时，总是0，则out.valid 就是readyGo

    in.ready := !valid || (out.ready && readyGo)
    !valid可以被省略，这样的话 当out不ready时，即使该级无数据，也不允许更新（没啥损失可能）
    所以 in.ready := out.ready && readyGo

    在LSPP中，只有D2可能不readyGo，而D2的out.ready恒1
        因此对于D2，in.ready = readyGo(!miss && !sbFull)
        对于其他各级 in.ready=out.ready，也等于(!miss || !sbFull)
    在SELDPP中，D2可能不readyGo，RF也可能不readyGo
        D2的readyGo = !miss && !sbFull
        RF的readyGo = !lsuRfValid
    因此，各级的in.ready是这样
        D2：!miss && !sbFull
        D1：!miss && !sbFull 
        RF：!miss && !sbFull && !lsuRfValid
    各级的out.valid是这样
        D2：!miss && !sbFull
        D1：1
        RF：!lsuRfValid
*/
```