pipeline {
  agent any
  tools {
    maven 'Maven-3.9'
  }
  stages {
    stage('Checkout') {
      steps {
        checkout scm
      }
    }
    stage('Build') {
      agent {
        docker {
          image 'registry.x1/j7beck/x1-maven3:jdk-1.8.0'
          args '-v $HOME/.m2/repository:/var/lib/jenkins/.m2/repository'
        }
      }
      steps {
        sh '$MAVEN_HOME/bin/mvn -B clean package jacoco:report'
        junit '**/target/surefire-reports/TEST-*.xml'
        recordCoverage(tools: [[parser: 'JACOCO']])
        stash name: 'coverage', includes: '**/jacoco.xml'
      }
    }
    stage('Publish') {
      tools {
        jdk 'JDK-17'
      }
      steps {
        withCredentials([usernameColonPassword(credentialsId: 'nexus', variable: 'USERPASS')]) {
          unstash name: 'coverage' 
          sh '''
            mvn -B -Prpm deploy site-deploy -DskipTests
            curl -u "$USERPASS" --upload-file target/rpm/wildfly-logstash/RPMS/noarch/wildfly-logstash-*.noarch.rpm https://www.x1/nexus/repository/x1-extra-rpms/testing/
          '''
          recordIssues tools: [spotBugs(pattern: 'target/spotbugsXml.xml')]
        }
      }
    }
    stage('Sonar') {
      tools {
        jdk 'JDK-21'
      }
      steps {
        unstash name: 'coverage' 
        sh 'mvn sonar:sonar -DskipTests -Dsonar.java.coveragePlugin=jacoco -Dsonar.jacoco.reportPath=target/jacoco.exec -Dsonar.host.url=https://www.x1/sonar'
      }
    }
  }
}
