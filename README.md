# Dupfind (Typelevel-Edition)

![Scala](https://img.shields.io/badge/Scala-3-red)
![License](https://img.shields.io/github/license/yourusername/yourproject)

Dupfind is a Scala application for identifying duplicate files on a local filesystem,
designed with performance and scalability in mind.
It is shared here as a "portfolio" project to show competency and familiarity with the typelevel stack.

## Features

- Built with Scala 3
- Typelevel stack: `cats`, `cats-effect`, `fs2`, etc.
- Parallel processing
- Optional caching with Postgres using `doobie`
- Configurable via `pureconfig` and `scopt`

## Basic Operation

Dupfind will check all files existing under a given directory 
(or multiple directories). 
Non-empty files with the same size will be grouped together for additional checking.
Get the MD5 checksums of same-size files.
Files with the same size and same checksums are considered to be identical.
(Actual file contents will not be directly compared for confirmation. 
Same size and checksum is considered good enough.) 
Processing will be done in parallel `fs2` streams. 
The number of concurrent workers can be configured.

Checksum calculation is the slowest part of the
application. So Dupfind can be run with an optional Postgres database for 
caching checksums. Files which have not changed since the last run can reuse 
the checksum calculated previously.  But if a file was 
modified, then the checksum needs to be recalculated.

## Additional Operations

Dupfind has a few additional database-specific operations:
1. Initialize the database, creating the table and index.
2. Prune the database, deleting records for files which no longer exist.
3. Identify records for missing files which no longer exist, but do not delete them.
4. Purge the database table, deleting all records in the table.

## Prerequisites
- Java 17+
- [sbt](https://www.scala-sbt.org/download.html)
- (Optional) Postgres or a Docker Postgres instance

## Basic Execution

Compile and run tests
```
sbt clean compile test
```

Create a jar file (for running without sbt)
```
sbt assembly
```

Run the application
```
java -jar target/scala-3.3.5/dupfind-cats-effect-assembly-0.1.0-SNAPSHOT.jar --no-db --dirs /some/directory
```

Scan multiple directories
```bash
java -jar target/scala-3.3.5/dupfind-cats-effect-assembly-0.1.0-SNAPSHOT.jar --no-db --dirs /some/dir,/another/dir,local/dir,../last/dir
```

Initialize a database
```bash
docker run -d -p 5432:5432 -e POSTGRES_PASSWORD=password postgres:17.4
java -jar target/scala-3.3.5/dupfind-cats-effect-assembly-0.1.0-SNAPSHOT.jar --init
```

Run with database caching (after DB initialization)
```bash
java -jar target/scala-3.3.5/dupfind-cats-effect-assembly-0.1.0-SNAPSHOT.jar --dirs /some/dir
```

Purge the database
```bash
java -jar target/scala-3.3.5/dupfind-cats-effect-assembly-0.1.0-SNAPSHOT.jar --dirs /some/dir --purge
```

Prune the database
```bash
java -jar target/scala-3.3.5/dupfind-cats-effect-assembly-0.1.0-SNAPSHOT.jar --dirs /some/dir --prune
```

Find missing database files
```bash
java -jar target/scala-3.3.5/dupfind-cats-effect-assembly-0.1.0-SNAPSHOT.jar --dirs /some/dir --missing
```

Print usage (shows other possible parameters)
```bash
java -jar target/scala-3.3.5/dupfind-cats-effect-assembly-0.1.0-SNAPSHOT.jar --help
```

## Postgres Container - common commands

```
docker rmi -f $(docker images -q) # remove images
docker run -d -p 5432:5432 -e POSTGRES_PASSWORD=password postgres:17.4
docker exec -it your_postgres_container_name bash
psql -U postgres
\l # show databases
\dt # show tables
\d files # describe files table
select * from files;
```

## Configuration
Default config can be found in `src/main/resources/application.conf`.
You can edit this file, rerun the assembly, and then run the application with the updated config.

## Ideas for future improvements
1. Add CI/CD
2. Use scala steward
3. Add a REST API using `http4s`

## License
Shared with the [MIT License]("https://opensource.org/license/MIT").
See LICENSE for details.
