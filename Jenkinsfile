pipeline {
  agent any
  tools {
    maven 'Maven-3.6'
    jdk 'JDK-1.8'
  }
  stages {
    stage('Checkout') {
      steps {
        checkout scm
      }
    }
    stage('Build') {
      steps {
        sh 'mvn clean package'
      }
    }
    stage('Publish') {
      steps {
        sh 'mvn -Prpm deploy site-deploy -DskipTests'
      }
    }
    stage('Sonar') {
      tools {
        jdk "JDK-11"
      }
      steps {
        sh 'mvn sonar:sonar -DskipTests -Dsonar.java.coveragePlugin=jacoco -Dsonar.jacoco.reportPath=target/jacoco.exec -Dsonar.host.url=https://www.x1/sonar'
      }
    }
  }
}
