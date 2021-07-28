# whipsaw

A library for creating, managing, processing, and reporting on very large workloads

## Deployment Info

This library is published as a jar file on Sonatype.org.  Any time we push to `master`, the Whipsaw jar is built and released to Sonatype as a snapshot.  This is all done with the help of [sbt-ci-release](https://github.com/olafurpg/sbt-ci-release).  From that site:

* git tag pushes are published as regular releases to Maven Central
* merge into main commits are published as -SNAPSHOT with a unique version number for every commit

So, in order to push a snapshot you would just push code to the master branch here.  To push a new release, you would push a tag like so:

```
git push origin v0.1.6
```

Newly published jars should be visible on the Sonatype repo via this [search](https://s01.oss.sonatype.org/#nexus-search;quick~whipsaw).

## Testing Info

The Whipsaw library has some tests that make use of the MongoDB persistence layer.  In order to be able to run the tests locally, you would need to have a MongoDB database up and running.  Assuming you have [docker](https://www.docker.com/) installed, this line should do the trick:

```
mkdir ~/mongo-data
docker run -d -p 27017:27017 -v ~/mongo-data:/data/db  mongo
```
