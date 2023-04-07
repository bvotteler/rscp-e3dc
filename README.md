# rscp-e3dc [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
A library to assist in constructing RSCP (remote storage control protocol) frames and data to communicate with E3DC servers.

RSCP is a proprietary protocol from [E3/DC GmbH](https://www.e3dc.com/).

This library is available under the [MIT license](./LICENSE).

### Changes to the original version of [bvotteler](https://github.com/bvotteler/rscp-e3dc)
- Integrated helper classes from
[sample application](https://github.com/bvotteler/rscp-e3dc-sample)
- changed build system to Gradle

## Requirements
* JDK 1.8+
* Gradle 7.5

## How to use it

### Add dependency
This module is **not** published to any public maven repository.
#### Using local maven repository
For local use,
you can publish module `rscp-e3dc` to your local maven repo by uncommenting lines in 
`build.gradle` and executing `gradle publishToMavenLocal`.

To the `build.gradle` file of your project, you have to add the local mvn repo 
and declare module dependency in the normal way:
```groovy
repositories {
    mavenCentral()
    mavenLocal()
}
dependencies {
   ...
    implementation 'io.github.bvotteler:e3dc-rscp:1.0.3.2'
   ...
}
```
Be aware that this is discouraged in 
[Gradle documentation](https://docs.gradle.org/current/userguide/declaring_repositories.html#sec:case-for-maven-local)

#### Using the jar file directly

A very simple way is to load project from github, build a jar file with `gradle jar` command
and copy this jar into your projects libs directory.
Then you can add a dependency in your `build.gradle`, eventually changing the version:

```groovy
    implementation files ("$projectDir/libs/e3dc-rscp-1.0.3.2.jar")
```

#### More alternatives
There are various other ways to add 
[Git repos as Gradle dependencies](https://alexvasilkov.com/gradle-git). 

### Constructing a frame
To construct a frame, we can use `RSCPData.Builder` in combination with `RSCPFrame.Builder`.

Typically, we want to start with an authentication frame like this: 
```java
// set user name (same as used to login to the portal)
RSCPData authUser = RSCPData.builder()
        .tag(RSCPTag.TAG_RSCP_AUTHENTICATION_USER)
        .stringValue(user)
        .build();

// add password (same as used to login to the portal)
RSCPData authPwd = RSCPData.builder()
        .tag(RSCPTag.TAG_RSCP_AUTHENTICATION_PASSWORD)
        .stringValue(password)
        .build();

// combine user/password into a authentication request container
RSCPData authContainer = RSCPData.builder()
        .tag(RSCPTag.TAG_RSCP_REQ_AUTHENTICATION)
        .containerValues(Arrays.asList(authUser, authPwd))
        .build();

// put the authentication request into a frame
RSCPFrame authFrame = RSCPFrame.builder()
        .addData(authContainer)
        .timestamp(Instant.now())
        .build();

// get byte array ready to be encrypted and sent to the server
byte[] frame = authFrame.getAsByteArray();
```

### Reading a received frame
Similarly, we can use the received, decrypted byte array to inspect the response.
```java
// assuming we have the response byte array in byte[] response...
RSCPFrame frame = RSCPFrame.builder()
        .buildFromRawBytes(response);

// get the contents of the frame
List<RSCPData> dataList = frame.getData();
// an authentication response contains a single data set with the authentication level
// as CHAR8 value which fits into a Java short
RSCPData authData = dataList.get(0);
// read optional short (will be empty if the data cannot be expressed as short)
Optional<Short> authLevel = authData.getValueAsInt();
```

### Sample project
[rscp-e3dc-sample][rscpsample] is a sample project showing how this library could be used.

It shows how to construct an authentication frame, as well as a database request frame.
In addition, it shows how to encrypt and decrypt frames sent to/received from E3DC servers.

[rscpsample]: https://github.com/bvotteler/rscp-e3dc-sample

## Typical Gradle tasks
### Build
Build the library (includes running tests) with:

`gradle build`

### Test
Run the tests with:

`gradle test` or `gradle check`

### Package as jar
To package the project (includes running tests), run:

`gradle jar`

### Create javadoc
`gradle javadoc`

### Clean all artifacts
`gradle clean`

### Publish to local mvn repository
You can also [configure your local repository][mvnlocal] for Maven, then run:

`gradle publishToMavenLocal`

[mvnlocal]: https://maven.apache.org/guides/mini/guide-configuring-maven.html#configuring-your-local-repository
