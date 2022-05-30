# SCC Verifier Open Api Converter

## 📜 Summary

SCC Verifier Open Api Converter allows us to use Spring Cloud Contract to generate contracts from an OpenApi yaml document.

Here is the documentation for these technologies:

- [Spring Cloud Contract](https://cloud.spring.io/spring-cloud-contract/reference/html/)
- [OpenApi](https://swagger.io/specification/)

## 🚀 Getting Started

In order to get this plugin working, you need the following things installed in your computer:

- Java 11 Version
- Maven

After you have these installed, you need to add the Spring Cloud Contract Maven Plugin in your pom.xml file and extend it with this Converter as a Dependency. Here is an easy example of a basic configuration:

```xml
<plugin>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-contract-maven-plugin</artifactId>
  <version>3.0.0</version>
  <extensions>true</extensions>
  <configuration>
    <packageWithBaseClasses>com.corunet.challenge.gameserver.baseclasses</packageWithBaseClasses>
    <contractsDirectory>${project.basedir}/src/main/resources/api</contractsDirectory>
  </configuration>
  <dependencies>
    <dependency>
      <groupId>com.corunet</groupId>
      <artifactId>scc-multiapi-converter</artifactId>
      <version>1.0.0</version>
      <scope>compile</scope>
    </dependency>
  </dependencies>
</plugin>
```

## 🧑🏻‍💻 Usage

Inside the configuration tag, you must declare the **contractsDirectory** tag. In this tag you must specify the directory that contains the OpenApi Yamls.

Also, we recommend specifying the **packageWithBaseClasses** tag. This tag indicates where your base class directory is located at and where your contract testing classes will be generated at. You should include at least one base class for the generated contract testing classes.

Once you specify this in your pom.xml you must use the command: **maven** **clean** **install**. This will generate the stubs necessary for the contract testing generation under the stub/mappings folder.

It will also generate the contractTesting class for the producer with the tests for your Api REST in the package indicated at the **packageWithBaseClasses** tag. If you want to change where your contracts will be located you must use the **basePackageForTests** tag in the pom.xml

If you need more control over the settings of your project, you can use all the Configuration Options that Spring Cloud Contract Maven Plugin has. These configuration options can be checked in the official documentation webpage under 4.2.7 Section: [Spring Cloud Contract Verifier Setup](https://cloud.spring.io/spring-cloud-contract/2.0.x/multi/multi__spring_cloud_contract_verifier_setup.html#maven-configuration-options)

## ✏️ Writing Ymls

This plugin supports most of the OpenApi/Swagger, but there are a couple of things that must be noted:

- Using **OpenApi´s example label** in the parameters/schemas will check in our contracts that the response is equal to the example value instead of using a Regex.
- Please be wary that writing an example must be the same type as indicated in the file, otherwise your contract will break.

This is an easy example of a small YAML that will work with our plugin:

```yaml
openapi: "3.0.0"
info:
  version: 1.0.0
  title: Corunet Challenge Game Server
  license:
    name: MIT
servers:
- url: http://localhost:8080/v1
paths:
  /games:
    post:
      summary: Start a Game
      operationId: createGame
      tags:
      - games
      responses:
        '200':
          content:
            application/json:
              schema:
                type: object
                properties:
                  code:
                    type: integer
                  message:
                    $ref: "#/components/schemas/Message"
components:
  schemas:
    Message:
      type: object
      properties:
        description:
          type: string
```

## ⚠️Current Limitations

Currently, this plugin has some limitations that will be addressed in the future. In order to make it work, we must follow some rules:

- This plugin allows the use of AllOfs and AnyOfs in the Response section. However, OpenApi does not support AllOfs in this section and AnyOf usage might not work depending on the OpenApi version you are using.
- Some OpenApi functionalities are not implemented yet, such as creating example objects, instead you must use the example tag in every property of the object

## 🌐 RoadMap

- Further investigation for OpenApi and Spring Cloud Contract possibilities.
- More testing and fixing possible bugs that may occur in the future.
- Version 1.1.0-SNAPSHOT has a draft version for AsyncApi and messaging contracts that is currently being developed. We appreciate any help given on this development 💜
