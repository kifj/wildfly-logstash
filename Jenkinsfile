pipeline {
  node {
    def mvnHome = tool 'Maven-3.3'
   
    stage('Checkout') {
      git url: 'https://github.com/kifj/wildfly-logstash.git', branch: 'master'
    }
  
    stage('Build') {
      sh "${mvnHome}/bin/mvn clean package"
    }
  
    stage('Publish') {
      sh "${mvnHome}/bin/mvn -Prpm deploy site-deploy -DskipTests" 
    }

    stage('Sonar') {
      sh "${mvnHome}/bin/mvn sonar:sonar -DskipTests -Dsonar.java.coveragePlugin=jacoco -Dsonar.jacoco.reportPath=target/jacoco.exec -Dsonar.host.url=https://www.x1/sonar"  
    }
  }
}
