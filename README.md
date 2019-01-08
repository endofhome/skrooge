[![Build Status](https://travis-ci.org/forty9er/skrooge.svg?branch=master)](https://travis-ci.org/forty9er/skrooge)

# Skrooge

### Work in progress
I chip away at this in waves, whether it is refactoring (usually) or adding features (occasionally). 

### Premise
A budgeting application to simplify and keep track of overcomplicated financial affairs. A user of this application might have several bank accounts and credit cards that they are regularly using. They want to budget expenditure over a year for several different categories of purchase, but find it hard to understand how they have spent their money.
Simply put this application allows the user to input normalised CSVs from several banks, an annual budget specified in JSON format, and categorise transactions on the CSVs. Categorisation suggestions are made, fairly crudely perhaps, by storing each categorisation and offering it again the next time the same merchant is seen.
Scripts are provided for several banks in order to normalise their CSV reports.
The back end provides an API serving a monthly report in JSON format. A basic frontend consumes data from this API and creates bar charts using C3.js.

I started writing this to keep track of my own affairs. I've learned about writing a larger Kotlin app from scratch, about the HTTP4K library, testing strategies and a little about HTTP. As is par for the course, I've also learned a few lessons about what *not* to do.

### How do I run it?

There are numerous environment variables required in order to run the application. I may at one time provide documentation about these but currently, if you really want to build and run this app, you're on your own. I don't expect anyone to actually want to do this so if you really, really do, please contact me and we can talk about it. That aside, the app can be run as follows:

* Clone the repo
* Build the code and run: 
- *all* the tests: `./build-and-test.sh`
- only the backend: `./gradlew clean test`
- only the frontend: `./build-and-test-frontend.sh`
* Run it `./gradlew run`

### Back-end logic flowchart

![backend-logic-flowchart](skrooge-logic-flowchart.jpg)
