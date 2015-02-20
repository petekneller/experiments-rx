//resolvers += "Typesafe Snapshots" at "http://boost-build-imac.local:8081/nexus/content/repositories/typesafe_maven_snapshots/"

resolvers += Resolver.sonatypeRepo("snapshots")

addSbtPlugin("org.ensime" % "ensime-sbt" % "0.1.5-SNAPSHOT")
