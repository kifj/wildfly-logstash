node {
   def mvnHome = tool 'Maven-3.3'
   
   stage 'Build'
   sh "${mvnHome}/bin/mvn clean package"
   
   stage 'Publish'
   sh "${mvnHome}/bin/mvn -Prpm deploy site-deploy -DskipTests" 
}
