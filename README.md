# Mutual-Inconsistency-Detection-In-Distributed-Systems-Group7




## Requirements




## Run





### Run customized experiments
You can choose to run the akka system with a sequence of operations defined by yourself, by simply passing a list of 
arguments to sbt in order.

Example of running customized experiments:
```
sbt AkkaMain.scala 24 upload-20-test.txt update-0-(0, 92830) split-10 split-{15,16,17,18,19,20} merge-{12,20}
```

The explanations of the arguments:

| Argument            | Explanation                                                                                                                                  |
|---------------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| 24                  | create 24 sites in the distributed system, site number 0~23                                                                                  |
| upload-20-test.txt  | upload one file named "test.txt" to site 20                                                                                                  |
| update-0-(0, 92830) | update file in site 0 with originpointer = (0, 92830)                                                                                        |
| split-10            | find the partition that contains site 10 and split the partition at that site   e.g. split-10 = List(Set("0", "1",.... "10"), Set("11", "12", ... "23")) |
| merge-{12,20}       | merge two partitions that contain site 12 and 20 respectively                                                                                |

Note that after the split, two new partitions will be generated:
{ siteName <= given siteName (lexically)}
{ siteName > given siteName (lexically)}

if the given *siteName* is already the largest in the current partition, or it is not a valid
siteName in the current system, nothing will happen.



### Run pre-defined experiments

To run a specific experiment:

```
sbt testOnly Experiment_name
```

The configuration of each experiment:

| Experiment_name | Sequence of Operations                                                | Expected Result after merge |
|-----------------|-----------------------------------------------------------------------|-----------------------------|
| Experiment1     | 4 sites, upload-0 split-1 update-0 update-2 update-3 merge-{0,2}      |                             |
| Experiment2     | 24 sites, upload-20 update-0 update-1 split-10 split-15 merge-{12,20} |                             |
| Experiment3     |                                                                       |                             |
| Experiment4     |                                                                       |                             |


## Run all experiments at once

Use the following command to run all the experiments at once.

```
sbt test
```