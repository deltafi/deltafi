spring:
  application:
    name: {{applicationName}}
  main:
    web-application-type: none
    banner-mode: "off"
  mustache:
    check-template-location: false

logging:
  level:
    org.springframework.data.convert.CustomConversions: ERROR
