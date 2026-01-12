# StreamEngine 适配VLIW

## 软件

TODO:扩展指令如何处理，如何打包成VLIW包

## Core

关键路径：

是否cal_stream -> fire cal_stream -> index

index -> issue?
index -> readData

### 译码

1. 保留rk rj vld信息，因为判断Bypass时需要
2. 保留isStream信息
3. 保留rd vld信息
4. 保留usebuffer信息
5. 保留cfg信息：是否cfg 是否load流 是否axi_load流
   1. TODO记得为cfg指令分配某个FU，使其能够完成配置
6. 保留stream op信息

### 分发TODO

1. 完成索引的获取（以便在发射段，使用索引判断是否可以发射）
2. 索引表异步更新


VLIW没有分发级
分发级完成的工作，要么新增一级流水完成，要么融入到译码级：
1. 需要该级能够明确 各槽位是否 **cal_stream fire**
   1. 使用 Stream Engine提供的icntmap接口，获取各cal_stream指令的index
   2. 根据fire的cal_stream数量(确切来说，是对于cal_stream各操作数确保fire的数量)，更新索引表


### 发射TODO

在IQ中使用索引判断是否可以发射

VLIW中没有IQ，只会判断流水线中出现的hazard进行Bypass或stall，没有所谓的发射逻辑
该级完成的工作，要么新增一级流水完成，要么融入到**获取index**那一级：
1.  利用索引**从readymap中读出flag判断**

### 读操作数TODO

VLIW中也有该级，在译码阶段完成，稍加修改就可使用
如前所述，可能会出现时序问题

### 执行与写回

基本无需更改

## StreamEngine

可以先针对VLIW Cache无限大的版本实现一版，后面再改
只不过评估出来的收益可能只有节省的lw指令数

### Store流

可复用：仅有一个store流，实现的逻辑是判断是否可以向axi发起写请求，以及写的过程

### Load流

1. 选择取数流：可复用
2. 与L2通过流水相连：TODO若VLIW实现L2，需根据VLIW-L2修改stream内部L2 load流水线，同时给L2增加一些端口和逻辑
3. 与AXI直接相连：TODO若VLIW实现AXI，主要是需要修改AXI的仲裁逻辑，并增加一些端口，stream内部与AXI的相连基本不需要改

