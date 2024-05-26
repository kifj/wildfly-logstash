pipeline {
  agent any
  tools {
    maven 'Maven-3.9'
    jdk 'JDK-17'
  }
  stages {
    stage('Checkout') {
      steps {
        checkout scm
      }
    }
    stage('Build') {
      steps {
        sh 'mvn -B clean package'
      }
    }
    stage('Publish') {
      steps {
        withCredentials([usernameColonPassword(credentialsId: 'nexus', variable: 'USERPASS')]) {
          sh '''
            mvn -B -Prpm deploy site-deploy -DskipTests
            curl -u "$USERPASS" --upload-file target/rpm/wildfly-logstash/RPMS/noarch/wildfly-logstash-*.noarch.rpm https://www.x1/nexus/repository/x1-extra-rpms/testing/
          '''
        }
      }
    }
    stage('Sonar') {
      tools {
        jdk 'JDK-17'
      }
      steps {
        sh 'mvn sonar:sonar -DskipTests -Dsonar.java.coveragePlugin=jacoco -Dsonar.jacoco.reportPath=target/jacoco.exec -Dsonar.host.url=https://www.x1/sonar'
      }
    }
  }
  post {
    always {
      junit '**/target/surefire-reports/TEST-*.xml'
      jacoco(execPattern: '**/**.exec')
      recordIssues tools: [spotBugs(pattern: 'target/spotbugsXml.xml')]
    }
  }
}
