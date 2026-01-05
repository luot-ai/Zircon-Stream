# stream开发整理

## 开发
1. coding
2. profiling/debug效率
3. 环境更新xx

## [V0](./V0/)

这一版是将Stream Engine接到乘除流水线，
在单周期完成 是否就绪的判断 以及 读buffer+算+写buffer的操作

基本完成测试，可循环测试向量加程序并与原始程序对比（但outer iteration=20时仍有bug）
   
## [V1](./V1/)

这一版是将 判断就绪 读buffer 算 写buffer拆开至不同流水级
在VADD上进行了初步验证

## [V2](./V2/)

这一版做了较大的改动，使其支持GEMM

## [VFFT](./VFFT/)

针对FFT的设计，停留在初步的分析阶段

## [当前工作](./onboard.md)

包含TODO和PLAN

## [细节考量](./细节考量.md)

偏实现的一些细节思考

## [正式文档](../正式/)

正式的文档，用于展示



