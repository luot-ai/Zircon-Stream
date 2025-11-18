1. A的一行*B的一列=C的一数，B向主存只能按行请求，因此算一个C需要若干行，所以必须要cache来缓存所有行中未使用的列
2. A：stream复用 B：cache复用
3. 过程：
   1. 先取A的一行
   2. 然后取B的若干行到cache
   3. B第一列以流的方式进1个算1个
   4. A row1 和 B col1算好后，说明B的所有行已经取到cache中
   5. 接着是主体
      1. A rowN 与B all col计算
      2. 取 A row_N+1 into stream
4. 实现细节
   1. 可以先不实现bypass，这样可以简化SE和L1之间的交互
   2. SE需要维护一个类似 coalescer的玩意儿，把一整个访存请求转换成多个字请求
      1. 实现bypass可能需要比较复杂的自动机