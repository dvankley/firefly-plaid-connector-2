# CONTRIBUTING

### Setup
Setup should be identical to any other Spring Boot/Gradle application. I recommend adding an additional configuration file (i.e. `application-dev.yml`) and enabling the corresponding Spring profile (i.e. `dev`) to allow you to persist and iterate on your local configuration.

I recommend setting up a local copy of Firefly for development purposes, especially one that you can easily backup and restore the database for to minimize your feedback loop on testing things.

### Guidelines
I don't currently have firm guidelines. I will adopt some if I ever get contributions. For now the main guideline is to have a test covering the changes you make.