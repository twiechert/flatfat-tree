# FlatFat Aggregation Tree
This is an implementation of the FlatFat tree as described in [1]. It can be used to prevent redundant computations in a sliding window setup
 of stream processors. Compared to the basic algorithm presented in the paper, this implementation allows the insertion of out-of-order elements

# Usage
Construct the FlatFat tree with the desired configuration. 

````$java
FlatFatTree<IN, ACC> tree = FlatFatTree.Builder.newBuilder(stateFactory)
            .initSlice(initSlice)
            .startEndNodeStrategy(ShiftingFlatFatTree.Builder.StartEndNodeStrategy.BINARY_SEARCH)
            .nodeByTimestampStrategy(ShiftingFlatFatTree.Builder.NodeByTimestampStrategy.BINARY_SEARCH)
            .capacity(1024).build();

````

ss
[1] http://www.vldb.org/pvldb/vol8/p702-tangwongsan.pdf