# Mutual-Inconsistency-Detection-In-Distributed-Systems-Group7

## Requirements

- Scala 2.12.\* (2.12.12+)
- JVM 1.8 compatible JDK
- SBT 1.4.9+

[unfinished]

## Build

Use the following command to build the docker image:

```
docker build -t group-7-midd .
```

### Run customized experiments

You can choose to run the akka system with a sequence of operations defined by yourself, by simply passing the
arguments in the right format in sbt shell. Steps and examples are given below:

Run/Create sbt shell from the created image:

```
docker run -it --rm group-7-midd ./sbt
```

Create and run 24 sites in the distributed system using `run` command in sbt shell. This created 10 sites with the names Site0, Site1, ..., Site9.

```
> run 24
```

Upload a file called `test.txt` to site 5:

```
> upload-Site5-test.txt
```

The origin pointer will be printed in the console so that it can be copy and pasted when the update command needs to be issued.

split the siteList into two partitions at site 10, with timeout set to 1000ms (optional argument), to give enough time to make sure the file list in each site is consistent. The thread is slept using the timeout value before and after the split in order to make sure that all the pending messages are done executing.
**List(Set(0, 1,.... 23)) ---> List(Set(0, 1,.... 10), Set(11, 12, ... 24))**

```
> spit-10-1000
```

or simply

```
> spit-10
```

> Note that after the split, two new partitions will be generated:
>
> > { siteName <= given `siteName` (lexically)}
> >
> > { siteName > given `siteName` (lexically)}
>
> if the given `siteName` is already the largest in the current partition, or it is not a valid
> siteName in the current system, nothing will happen.

split the siteList into two partitions at site 15, with timeout 1000
**List(Set(0, 1,.... 10), Set(11, 12, ... 24)) ---> List(Set(0, 1,.... 10), Set(11, 12, 13, 14, 15), Set(16, 17, ... 24))**

```
> spit-15-1000
```

update the file with origin pointer `(siteName,timestamp)` in site 12

```
> update-12-(12,90300)
```

merge the partitions that contain 10,22,11 respectively (in this case, all the three partitions will be merged together)
let site 12 send its file list to site 20, so that site 20 can check if its file list is consistent with that of site 12,
and deal with the inconsistency if there is any. Add a 1500ms `timeout` (optional)
**List(Set(0, 1,.... 10), Set(11, 12, 13, 14, 15), Set(16, 17, ... 24)) ------> List(Set(0, 1,.... 23))**

```
> merge-12-20-(10,22,11)-1500
```

[change?]

### Run pre-defined experiments

To run a specific experiment:

```
testOnly Experiment_name
```

The configuration of each experiment:

| Experiment_name | Sequence of Operations                                                                                              | Expected Result after merge |
| --------------- | ------------------------------------------------------------------------------------------------------------------- | --------------------------- |
| Experiment1     | 4 sites, upload-0-test.txt split-1-1000 update-0-(0,ts1) update-2-(2,ts1) update-3-(3,ts1) merge-0-2-(0,1,2,3)-1000 | (0->1,1->0,2->1,3->1)       |
| Experiment2     | 24 sites, upload-20 update-0 update-1 split-10 split-15 merge-{12,20}                                               |                             |
| Experiment3     |                                                                                                                     |                             |
| Experiment4     |                                                                                                                     |                             |

the run time of each experiment will be printed in the console after it is finished.

## Run all experiments at once

Use the following command to run all the experiments at once.

```
sbt test
```
