# WireMock State Extension Demo

<p align="center">
    <a href="https://wiremock.org" target="_blank">
        <img width="512px" src="https://wiremock.org/images/logos/wiremock/logo_wide.svg" alt="WireMock Logo"/>
    </a>
</p>

This is a demonstration of the WireMock State Extension, showcasing its initial feature set and usage examples.

## General Overview

- **Watch the Demo Video**: [WireMock State Extension Demo Video](https://www.youtube.com/watch?v=OUrMEpzHbvY)
- **View the Slides**: [WireMock State Extension Slides](https://github.com/wiremock/wiremock-state-extension/blob/develop/demo/wiremock_state_extension_webinar.pdf)

## Initial Feature Set: The State

- Creating, updating, and deleting a state.
- Storing different states in association with a context.
- Request matcher to check for the existence of a context.
- Handlebars support.

## State

A context can have multiple properties. Each property can be overwritten individually.

### Context

The parent container of a state. Multiple contexts can exist.

### Example: Modeling Queues

- Adding new (unmodifiable) states to the context.
- Insert as the first or last item.
- Read access to the first/last/index.
- Delete first/last/index.
- Check size.
- Suggested by @ioanngolovko.

### StateList

### Example: Improving Usability

- Allow defining default values (suggested by @alexandre-chopin).
- Expose the "list" to Handlebars.
- Allow building listing endpoints.
- Query "special" properties: updateCount, listSize, list, property existence.
- Delete single properties.
- Loadable in WireMock standalone and Docker container.

<p align="center">
    <a>      
        <img width="512px" src="demo/Images/Example1.png" alt="Example1"/>
    </a>
</p>

<p align="center">
    <a>      
        <img width="512px" src="demo/Images/Example2.png" alt="Example2"/>
    </a>
</p>

## Getting Started

To get started with the WireMock State Extension, follow these steps:

1. [Install WireMock](https://github.com/wiremock/wiremock) if you haven't already.
2. [Install the State Extension](link-to-extension) by following the installation instructions.
3. Use the provided examples to understand how to create, manage, and interact with states.

## Running the Demo Locally

To run the WireMock State Extension demo locally, follow these steps:

### Prerequisites

Before you begin, ensure you have the following prerequisites installed on your system:

1. **Java Development Kit (JDK)**: WireMock is a Java application, so you'll need the JDK installed. You can download it from the [official Oracle website](https://www.oracle.com/java/technologies/javase-downloads.html) or use an open-source distribution like OpenJDK.

2. **Git**: You'll need Git to clone the WireMock State Extension repository. If you don't have Git installed, you can download it from the [official website](https://git-scm.com/).

### Step 1: Clone the WireMock State Extension Repository

```shell
git clone https://github.com/wiremock/wiremock-state-extension.git
cd wiremock-state-extension
```

### Step 2: Build the Project

From directory of this README:

```shell
cd ..
./gradlew build
```

### Step 3: download wiremock standalone

From directory of this README:

```shell
cd ..
curl -o build/libs/wiremock-standalone-3.6.0.jar https://repo1.maven.org/maven2/org/wiremock/wiremock-standalone/3.6.0/wiremock-standalone-3.6.0.jar
```

### Step 4: Start WireMock with the State Extension

From directory of this README:

```shell
cd ..
java -cp build/libs/wiremock-state-extension-standalone-0.6.0-SNAPSHOT.jar:build/libs/wiremock-standalone-3.6.0.jar wiremock.Run --verbose --global-response-templating --root-dir demo/stubs
```

This command starts WireMock with the State Extension enabled.

### Access the Demo

The WireMock State Extension demo is now running locally. You can access it through a web browser or make API requests as needed. Refer to the demo video and slides for more information on using the extension.

That's it! You've successfully set up and run the WireMock State Extension demo on your local machine.

## Usage

You can start experimenting with the WireMock State Extension by using the provided examples. Explore the capabilities of the extension for modeling queues and improving usability.

## Contributions

Contributions and feedback are welcome. If you have ideas or suggestions for improving the WireMock State Extension, feel free to contribute or share your thoughts.

This is a README file with additional content. The page now includes a general overview, links to the demo video and slides, and instructions on how to run the demo locally.



