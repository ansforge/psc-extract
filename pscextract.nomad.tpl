job "pscextract" {
  datacenters = ["${datacenter}"]
  type = "service"

  vault {
    policies = ["psc-ecosystem"]
    change_mode = "restart"
  }

  group "pscextract-services" {
    count = "1"

    constraint {
      attribute = "$\u007Bnode.class\u007D"
      value     = "data"
    }

    update {
      max_parallel = 1
      min_healthy_time = "30s"
      progress_deadline = "5m"
      healthy_deadline = "2m"
    }

    network {
      port "http" {
        to = 8080
      }
    }

    task "prep-volume" {
      driver = "docker"
      config {
        image = "busybox:latest"
        mount {
          type = "volume"
          target = "/app/extract-repo"
          source = "pscextract-data"
          readonly = false
          volume_options {
            no_copy = false
            driver_config {
              name = "pxd"
              options {
                io_priority = "high"
                size = 10
                repl = 3
              }
            }
          }
        }
        command = "sh"
        args = ["-c", "mkdir -p /app/extract-repo/working-directory && chown -R 1:1 /app/extract-repo/working-directory"]
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
        JAVA_TOOL_OPTIONS = "-Dspring.config.location=/secrets/application.properties -Xms256m -Xmx1792m -XX:+UseG1GC"
      }
      config {
        image = "${artifact.image}:${artifact.tag}"
        volumes = [
          "name=pscextract-data,io_priority=high,size=10,repl=3:/app/extract-repo"
        ]
        volume_driver = "pxd"
        ports = ["http"]
      }
      template {
        destination = "local/file.env"
        env = true
        data = <<EOF
PUBLIC_HOSTNAME={{ with secret "psc-ecosystem/pscextract" }}{{ .Data.data.public_hostname }}{{ end }}
EOF
      }
      template {
        data = <<EOF
server.servlet.context-path=/pscextract/v1
mongodb.host={{ range service "psc-mongodb" }}{{ .Address }}{{ end }}
mongodb.port={{ range service "psc-mongodb" }}{{ .Port }}{{ end }}
mongodb.name=mongodb
mongodb.username={{ with secret "psc-ecosystem/mongodb" }}{{ .Data.data.root_user}}{{ end }}
mongodb.password={{ with secret "psc-ecosystem/mongodb" }}{{ .Data.data.root_pass}}{{ end }}
mongodb.admin.database=admin
files.directory=/app/extract-repo
working.directory=/app/extract-repo/working-directory
extract.name=Extraction_Pro_sante_connect
extract.test.name=Extraction_Pro_sante_connect_cartes_de_test_bascule
server.servlet.context-path=/pscextract/v1
spring.mail.host={{ with secret "psc-ecosystem/emailing" }}{{ .Data.data.spring_mail_host }}{{ end }}
spring.mail.port={{ with secret "psc-ecosystem/emailing" }}{{ .Data.data.spring_mail_port }}{{ end }}
spring.mail.username={{ with secret "psc-ecosystem/emailing" }}{{ .Data.data.spring_mail_username }}{{ end }}
spring.mail.password={{ with secret "psc-ecosystem/emailing" }}{{ .Data.data.spring_mail_password }}{{ end }}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
pscextract.mail.receiver={{ with secret "psc-ecosystem/emailing" }}{{ .Data.data.mail_receiver }}{{ end }}
EOF
        destination = "secrets/application.properties"
      }
      resources {
        cpu = 1000
        memory = 2148
      }
      service {
        name = "$\u007BNOMAD_JOB_NAME\u007D"
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
LOGSTASH_HOST = {{ range service "logstash" }}{{ .Address }}:{{ .Port }}{{ end }}
ENVIRONMENT = "${datacenter}"
EOH
        destination = "local/file.env"
        env = true
      }
      config {
        image = "prosanteconnect/filebeat:7.14.2"
      }
    }
  }
}
