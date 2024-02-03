job "pscextract" {
  datacenters = ["${datacenter}"]
  type = "service"
  namespace = "${nomad_namespace}"

  vault {
    policies = ["psc-ecosystem"]
    change_mode = "restart"
  }

  group "pscextract-services" {
    count = "1"

    // Volume portworx CSI
    volume "pscextract" {
      attachment_mode = "file-system"
      access_mode     = "single-node-writer"
      type            = "csi"
      read_only       = false
      source          = "vs-${nomad_namespace}-pscextract-data"
    }

    affinity {
      attribute = "$\u007Bnode.class\u007D"
      value     = "compute"
    }

    network {
      port "http" {
        to = 8080
      }
    }

    task "prep-volume" {
      driver = "docker"

      // Monter le volume portworx CSI 
      volume_mount {
        volume      = "pscextract"
        destination = "/app/extract-repo"
        read_only   = false
      }

      config {
        image = "busybox:latest"
        command = "sh"
        args = ["-c", "mkdir -p /app/extract-repo/working-directory && chown -R 1:1 /app/extract-repo"]
      }
      resources {
        cpu = 200
        memory = 128
      }
      lifecycle {
        hook = "prestart"
        sidecar = "false"
      }
    }

    task "pscextract" {
      restart {
        attempts = 3
        delay = "60s"
        interval = "1h"
        mode = "fail"
      }
      driver = "docker"
      env {
        JAVA_TOOL_OPTIONS = "-Dspring.config.location=/secrets/application.properties -Xms256m -Xmx2048m -XX:+UseG1GC"
      }
      config {
        extra_hosts = [ "psc-api-maj.internal:$\u007BNOMAD_IP_http\u007D" ]
        image = "${artifact.image}:${artifact.tag}"
        volumes = [
          "name=${nomad_namespace}-pscextract-data,io_priority=high,size=10,repl=3:/app/extract-repo"
        ]
        volume_driver = "pxd"
        ports = ["http"]
      }
      template {
        destination = "local/file.env"
        env = true
        data = <<EOF
PUBLIC_HOSTNAME={{ with secret "psc-ecosystem/${nomad_namespace}/pscextract" }}{{ .Data.data.public_hostname }}{{ end }}
EOF
      }
      template {
        data = <<EOF
server.servlet.context-path=/pscextract/v1
mongodb.host={{ range service "${nomad_namespace}-psc-mongodb" }}{{ .Address }}{{ end }}
mongodb.port={{ range service "${nomad_namespace}-psc-mongodb" }}{{ .Port }}{{ end }}
mongodb.name=mongodb
mongodb.username={{ with secret "psc-ecosystem/${nomad_namespace}/mongodb" }}{{ .Data.data.root_user}}{{ end }}
mongodb.password={{ with secret "psc-ecosystem/${nomad_namespace}/mongodb" }}{{ .Data.data.root_pass}}{{ end }}
mongodb.admin.database=admin

files.directory=/app/extract-repo
working.directory=/app/extract-repo/working-directory
api.base.url={{ range service "${nomad_namespace}-psc-api-maj-v2" }}http://{{ .Address }}:{{ .Port }}/psc-api-maj/api{{ end }}

extract.name=Extraction_Pro_sante_connect
extract.test.name={{ with secret "psc-ecosystem/${nomad_namespace}/pscextract" }}{{ .Data.data.test_file_name }}{{ end }}
page.size=50000
first.name.count=3
server.servlet.context-path=/pscextract/v1

spring.mail.host={{ with secret "psc-ecosystem/${nomad_namespace}/admin" }}{{ .Data.data.mail_server_host }}{{ end }}
spring.mail.port={{ with secret "psc-ecosystem/${nomad_namespace}/admin" }}{{ .Data.data.mail_server_port }}{{ end }}
spring.mail.username={{ with secret "psc-ecosystem/${nomad_namespace}/admin" }}{{ .Data.data.mail_username }}{{ end }}
spring.mail.password={{ with secret "psc-ecosystem/${nomad_namespace}/admin" }}{{ .Data.data.mail_password }}{{ end }}
spring.mail.properties.mail.smtp.auth={{ with secret "psc-ecosystem/${nomad_namespace}/admin" }}{{ .Data.data.mail_smtp_auth }}{{ end }}
spring.mail.properties.mail.smtp.starttls.enable={{ with secret "psc-ecosystem/${nomad_namespace}/admin" }}{{ .Data.data.mail_enable_tls }}{{ end }}
pscextract.mail.receiver={{ with secret "psc-ecosystem/${nomad_namespace}/admin" }}{{ .Data.data.mail_receiver }}{{ end }}
secpsc.environment={{ with secret "psc-ecosystem/${nomad_namespace}/admin" }}{{ .Data.data.platform }}{{ end }}

{{ with secret "psc-ecosystem/${nomad_namespace}/admin" }}logging.level.fr.ans.psc={{ .Data.data.log_level }}{{ end }}
EOF
        destination = "secrets/application.properties"
      }
      resources {
        cpu = 1000
        memory = 2560
      }
      service {
        name = "$\u007BNOMAD_NAMESPACE\u007D-$\u007BNOMAD_JOB_NAME\u007D"
        tags = ["urlprefix-$\u007BPUBLIC_HOSTNAME\u007D/pscextract/v1/"]
        port = "http"
        check {
          type = "http"
          path = "/pscextract/v1/check"
          port = "http"
          interval = "30s"
          timeout = "2s"
          failures_before_critical = 5
        }
      }
    }
    task "log-shipper" {
      driver = "docker"
      restart {
        interval = "30m"
        attempts = 5
        delay    = "15s"
        mode     = "delay"
      }
      meta {
        INSTANCE = "$\u007BNOMAD_ALLOC_NAME\u007D"
      }
      template {
        data = <<EOH
LOGSTASH_HOST = {{ range service "${nomad_namespace}-logstash" }}{{ .Address }}:{{ .Port }}{{ end }}
ENVIRONMENT = "${datacenter}"
EOH
        destination = "local/file.env"
        env = true
      }
      config {
        image = "${registry_username}/filebeat:7.14.2"
      }
    }
  }
}
