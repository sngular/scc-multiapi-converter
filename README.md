[![Codacy Badge](https://app.codacy.com/project/badge/Grade/0d331d782ff849f1bdf6d71f60203eff)](https://www.codacy.com/gh/corunet/scc-multiapi-converter/dashboard?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=corunet/scc-multiapi-converter&amp;utm_campaign=Badge_Grade)
[![Maven Central](https://img.shields.io/maven-central/v/net.coru/scc-multiapi-converter.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22net.coru%22%20AND%20a:%22scc-multiapi-converter%22)
# SCC Verifier MultiApi Converter

## 📜 Summary

SCC Verifier MultiApi Converter allows us to use Spring Cloud Contract to generate contracts from an OpenApi yaml document or an AsyncApi document. This plugin will detect automatically what contract needs to be generated

Here is the documentation for these technologies:

- [Spring Cloud Contract](https://docs.spring.io/spring-cloud-contract/docs/current/reference/html/)
- [OpenApi](https://swagger.io/specification/)
- [AsyncApi](https://www.asyncapi.com/docs/getting-started)

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
      <groupId>net.coru</groupId>
      <artifactId>scc-multiapi-converter</artifactId>
      <version>2.7.1</version>
      <scope>compile</scope>
    </dependency>
  </dependencies>
</plugin>
```

## 🧑🏻‍💻 Usage

Inside the configuration tag, you must declare the **contractsDirectory** tag. In this tag you must specify the directory that contains the OpenApi/AsyncApi Yamls.

Also, we recommend specifying the **baseClassForTests** tag. This tag indicates the base class from which the autogenerated test will be extended. This class must be a test class of the producer part in which only a call to the producer method will be made, which is necessary for the test of the producer of the autogenerated class.

Once you specify this in your pom.xml you must use the command: **mvn** **clean** **install**. This will generate the stubs necessary for the contract testing generation under the stubs folder.

It will also generate the ContractVerifierTest class for the producer and consumer tests for your Messaging Api and your Rest Api, in addition to the corresponding yml files with the contracts of both, inside target in the package where your baseClassForTests is located. If you want to change where your contracts will be located you must use the basePackageForTests tag in the pom.xml.

If you need more control over the settings of your project, you can use all the Configuration Options that Spring Cloud Contract Maven Plugin has. These configuration options can be checked in the official documentation webpage under 4.2.7 Section: [Spring Cloud Contract Verifier Setup](https://cloud.spring.io/spring-cloud-contract/2.0.x/multi/multi__spring_cloud_contract_verifier_setup.html#maven-configuration-options).

## ✏️ Writing Ymls

This plugin supports most of the OpenApi/Swagger and AsyncApi, but there are a couple of things that must be noted:

- Using **OpenApi/AsyncApi´s example label** in the parameters/schemas will check in our contracts that the response is equal to the example
value instead of using a Regex.
- **OpenApi**: Since 2.4.0 version, we support the definition of parameters in both Path and Operation object, but you can only define it 
in one of them. ❗❗❗️ We use the Option resolver from OpenApi which will override the Operation parameters if you have a parameter defined in the Path.
- Please be aware that writing an example must be the same type as indicated in the file, otherwise your contract will break.

This is an easy example of a small YAML for OpenApi that will work with our plugin:

```yaml
---
openapi: "3.0.0"
info:
  version: 1.0.0
  title: Corunet Challenge Game Server
  description: Test File for SCC MultiApi Plugin.
  contact:
    name: Corunet
    url: coru.net
    email: info@coru.net
  license:
    name: MPL 2.0
servers:
- url: http://localhost:8080/v1
paths:
  /games:
    summary: Hola
    get:
      summary: List all available games
      description: Test File for SCC MultiApi Plugin.
      tags:
      - games
      operationId: listGames
      responses:
        '200':
          description: A paged array of games
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/Game"
components:
  schemas:
    Game:
      type: object
      properties:
        player:
          $ref: "#/components/schemas/Player"
    Player:
      type: object
      properties:
        name:
          $ref: "#/components/schemas/Name"
    Name:
      type: object
      properties:
        firstname:
          type: string
          example: John
        lastname:
          type: string
          example: Doe
tags:
- name: games
  description: Test description for SCC MultiApi Plugin.
```

And here, there is an example of a YAML file for the AsyncApi side:

```yaml
asyncapi: "2.3.0"
info:
  title: Order Service
  version: 1.0.0
  description: Order management Service
  contact:
    name: Corunet
    url: http://www.asyncapi.org/support
    email: info@coru.net
    license:
      name: MPL 2.0
channels:
  orderCreated:
    publish:
      operationId: "publishOperation"
      message:
        $ref: '#/components/messages/OrderCreated'
    description: Operation that will produce an OrderCreated object
  createOrder:
    subscribe:
      operationId: "subscribeOperation"
      message:
        $ref: '#/components/messages/CreateOrder'
    description: Operation that will consume an CreateOrder object
components:
  messages:
    OrderCreated:
      payload:
        $ref: '#/components/schemas/Order'
    CreateOrder:
      payload:
        $ref: '#/components/schemas/Order'
  schemas:
    Order:
      type: object
      properties:
        tableNumber:
          type: integer
          format: int32
          example: 1
        clientRef:
          type: integer
          format: int32
          example: 432
```

## ⚠️Current Limitations

Currently, this plugin has some limitations that will be addressed in the future. In order to make it work, we must follow some rules:

**OpenApi implementation**:

- Some OpenApi functionalities are not implemented yet, such as creating example objects, instead you must use the example tag in every property of the object.
- Due to the OpenApi Parser code, when you use a $ref that points to an external file, there are some limitations when using $ref again in that same file.

**Async Api implementation**:

- This plugin doesn't allow the use of AllOfs, OneOfs and AnyOfs.
- Support for generating contracts from avro files.
- Some AsyncApi functionalities are not implemented yet, such as creating example objects, instead you must use the example tag in every property of the object.

## 🌐 RoadMap

- Add implementation to support AllOfs, AnyOfs and OneOfs in AsyncApi.
- Add support for generating contracts from avro files.
- Further investigation for OpenApi/AsyncApi and Spring Cloud Contract possibilities.
- More testing and fixing possible bugs that may occur in the future.
- Get rid of the OpenApi parser in order to control our own code.
