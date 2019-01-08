[![Build Status](https://travis-ci.org/forty9er/skrooge.svg?branch=master)](https://travis-ci.org/forty9er/skrooge)

# Skrooge

### Work in progress

### Premise
A budgeting application to simplify and keep track of overcomplicated financial affairs. A user of this application might have several bank accounts and credit cards that they are regularly using. They want to budget expenditure over a year for several different categories of purchase, but find it hard to understand how they have spent their money.
Simply put this application allows the user to input normalised CSVs from several banks, an annual budget specified in JSON format, and categorise transactions on the CSVs. Categorisation suggestions are made, fairly crudely perhaps, by storing each categorisation and offering it again the next time the same merchant is seen.
Scripts are provided for several banks in order to normalise their CSV reports.
The back end provides an API serving a monthly report in JSON format. A basic frontend consumes data from this API and creates bar charts using C3.js.

I started writing this to keep track of my own affairs. I've learned about writing a larger Kotlin app from scratch, about the HTTP4K library, testing strategies and a little about HTTP. As is par for the course, I've also learned a few lessons about what *not* to do.

### Back-end logic flowchart

![backend-logic-flowchart](skrooge-logic-flowchart.jpg)
