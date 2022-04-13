# Mutual Inconsistency Detection In Distributed Systems Group7

## Requirements

- Scala 2.12.\* (2.12.12+)
- JVM 1.8 compatible JDK
- SBT 1.4.9+

## Build

Use the following command to build the docker image:

```
docker build -t group-7-midd .
```

## Run customized experiments

You can choose to run the akka system with a sequence of operations defined by yourself, by simply passing the
arguments in the right format in sbt shell. The format of the expected commands that can be issues against the akka system is the following:

```
upload-<ARG: siteName (must exist)>-<ARG: fileName>

update-<ARG: the site name that will update the file>-<ARG: origin pointer of the file to update>

split-<ARG: site name where the split needs to happen>-<ARG OPTIONAL: timeout after/before split>

merge-<ARG: sitename from which to send filelist to other partition>-<ARG: siteName that should get the filelist from the sending siteName>-{<ARG: partition set that needs to be merged e.g. 10,22,11>}-<ARG OPTIONAL: timeout after/before merge>
```
Steps and examples are given below:

Run/Create sbt shell from the created image:

```
docker run -it --rm group-7-midd ./sbt
```

Create and run 24 sites in the distributed system using `run` command in sbt shell. This creates 24 sites with the names Site0, Site1, ..., Site23.

```
> run 24
```

Upload a file called `test.txt` to site 5:

```
> upload-Site5-test.txt
```

The origin pointer will be printed in the console so that it can be copied and pasted when the update command needs to be issued.

split the siteList into two partitions at site 10, with timeout set to 1000ms by default (optional argument), to give enough time to make sure the file list in each site is consistent. The thread is slept using the timeout value before and after the split in order to make sure that all the pending messages are done executing.

```
> split-Site10-1000
```

or simply

```
> split-Site10
```

Will result in the following partition list:  

**List(Set(0, 1,.... 23)) ---> List(Set(0, 1,.... 10), Set(11, 12, ... 24))**

Now splitting the partition list into two partition sets at site 15, with timeout 2000

```
> split-Site15-2000
```

Results in the following partition list:

**List(Set(0, 1,.... 10), Set(11, 12, ... 24)) ---> List(Set(0, 1,.... 10), Set(11, 12, 13, 14, 15), Set(16, 17, ... 24))**


> Note that after the split, two new partition sets will be generated:
>
> > { siteName <= given `siteName` (lexically) }
> >
> > { siteName > given `siteName` (lexically) }
>
> if the given `siteName` is already the largest in the current partition, or it is not a valid
> siteName in the current system, nothing will happen.

Update the file with origin pointer `(siteName,timestamp)` e.g. (12,90300) in site 12 can be done using the following command:

```
> update-Site12-(Site12,90300)
```

To merge, for example, the partition sets `{Site0, Site1, ..., Site}`  (in this case, all the three partitions will be merged together)
let site 12 send its file list to site 20, so that site 20 can check if its file list is consistent with that of site 12,
and deal with the inconsistency if there is any. Add a 1500ms `timeout` (optional)
**List(Set(0, 1,.... 10), Set(11, 12, 13, 14, 15), Set(16, 17, ... 24)) ------> List(Set(0, 1,.... 23))**

```
> merge-12-20-1500
```

### Run Experiments

To run a specific experiment, replace `<ExperimentName>` with the experiment that you want to run in the following command in sbt shell:

```
testOnly *<ExperimentName>
```
e.g.
```
testOnly *ExperimentTimestamps
```

Use the following command to run all the experiments at once.

```
docker run -it --rm group-7-midd ./sbt test
```


There are two experiments: ExperimentTimestamps and ExperimentVersionVector. 

[//]: # (The configuration of each experiment:)

[//]: # (| Experiment_name | Sequence of Operations                                                                                              | Expected Result after merge |)

[//]: # (| --------------- | ------------------------------------------------------------------------------------------------------------------- | --------------------------- |)

[//]: # (| Experiment1     | 4 sites, upload-0-test.txt split-1-1000 update-0-&#40;0,ts1&#41; update-2-&#40;2,ts1&#41; update-3-&#40;3,ts1&#41; merge-0-2-&#40;0,1,2,3&#41;-1000 | &#40;0->1,1->0,2->1,3->1&#41;       |)

[//]: # (| Experiment2     | 24 sites, upload-20 update-0 update-1 split-10 split-15 merge-{12,20}                                               |                             |)

[//]: # (| Experiment3     |                                                                                                                     |                             |)

[//]: # (| Experiment4     |                                                                                                                     |                             |)

the run time of each experiment will be printed in the console after it is finished.
