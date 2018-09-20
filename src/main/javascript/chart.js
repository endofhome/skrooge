import colourtron from './colourtron'
import dataForCategory from './dataFinder'

const year = document.getElementsByClassName("year")[0].textContent;
const monthNumber = document.getElementsByClassName("month-number")[0].textContent;

const monthlyReportData = async () => {
    const response = await fetch('http://localhost:5000/monthly-report/json?year=' + year + '&month=' + monthNumber);
    return await response.json()
};

function generateCategory(title, categoryData, binding, height) {
    const keysValue = (title === 'Annual overview') ? ['actual', 'budget', 'annualBudget'] : ['actual', 'budget'];

    if (categoryData.length > 0) {
        const c3Data = {
            bindto: binding,
            title: {
                text: title
            },
            data: {
                labels: true,
                json: categoryData,
                keys: {
                    x: 'name',
                    value: keysValue
                },
                type: 'bar',
                color: (color, d) => {
                    return colourtron(d, categoryData)
                }
            },
            bar: {
                width: {
                    ratio: 0.5
                }
            },
            axis: {
                rotated: true,
                x: {
                    type: 'category'
                }
            }
        };

        if (height !== null) {
            c3Data.size = {
                height: height
            }
        }

        c3.generate(c3Data);
    }
}

const reformatForAnnualOverview = (genericFormatData) => {
    return [{ name: genericFormatData.name, actual: genericFormatData.yearToDateActual, budget: genericFormatData.yearToDateBudget, annualBudget: genericFormatData.annualBudget }]
};

const reformatForMonthOverview = (genericFormatData) => {
    return [{ name: genericFormatData.name, actual: genericFormatData.actual, budget: genericFormatData.budget }]
};

monthlyReportData().then((result => {
    generateCategory("Annual overview", reformatForAnnualOverview(result.aggregateOverview.data), "#annual-overview");
    generateCategory("Month overview", reformatForMonthOverview(result.aggregateOverview.data), "#month-overview");
    generateCategory("Month breakdown", result.overview.data, "#month-breakdown", 1000);
    generateCategory("In your home", dataForCategory("In your home", result.categories), "#in-your-home", 1000);
    generateCategory("Insurance", dataForCategory("Insurance", result.categories), "#insurance");
    generateCategory("Motoring and public transport", dataForCategory("Motoring and public transport", result.categories), "#motoring-and-public-transport");
    generateCategory("Savings and investments", dataForCategory("Savings and investments", result.categories), "#savings-and-investments");
    generateCategory("Family", dataForCategory("Family", result.categories), "#family");
    generateCategory("Fun", dataForCategory("Fun", result.categories), "#fun");
    generateCategory("Health and beauty", dataForCategory("Health and beauty", result.categories), "#health-and-beauty");
    generateCategory("Clothes", dataForCategory("Clothes", result.categories), "#clothes");
    generateCategory("Big one offs", dataForCategory("Big one offs", result.categories), "#big-one-offs");
    generateCategory("Odds and sods", dataForCategory("Odds and sods", result.categories), "#odds-and-sods");
    generateCategory("Refurbishments", dataForCategory("Refurbishments", result.categories), "#refurbishments");
}));
