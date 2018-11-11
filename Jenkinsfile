pipeline {
  agent any
  stages {
    stage('Checkout') {
      steps {
        git(url: 'https://github.com/kifj/wildfly-logstash.git', branch: 'master', changelog: true)
      }
    }
    stage('Build') {
      steps {
        sh '${mvnHome}/bin/mvn clean package'
      }
    }
    stage('Publish') {
      steps {
        sh '${mvnHome}/bin/mvn -Prpm deploy site-deploy -DskipTests'
      }
    }
    stage('Sonar') {
      steps {
        sh '${mvnHome}/bin/mvn sonar:sonar -DskipTests -Dsonar.java.coveragePlugin=jacoco -Dsonar.jacoco.reportPath=target/jacoco.exec -Dsonar.host.url=https://www.x1/sonar'
      }
    }
  }
  environment {
    mvnHome = tool 'Maven-3.6'
  }
}
