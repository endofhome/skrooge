import colourtron from './colourtron'

const year = document.getElementsByClassName("year")[0].textContent;
const monthNumber = document.getElementsByClassName("month-number")[0].textContent;

const monthlyReportData = async () => {
    const response = await fetch('http://localhost:5000/monthly-report/json?year=' + year + '&month=' + monthNumber);
    return await response.json()
};

const _dataForCategory = (title, categories) => {
    const matchingCategories = categories.filter(item => item.title === title);
    if (matchingCategories.length === 1) {
        return matchingCategories[0].data
    } else if (matchingCategories.length === 0) {
        console.log('No matching categories for title:' + title);
        return []
    } else if (matchingCategories.length > 1) {
        throw {
            name: 'Too many matching categories',
            message: matchingCategories.length + ' matching categories for title: ' + title
        };
    }
};

function generateCategory(title, categories, binding, height) {
    const dataForCategory = _dataForCategory(title, categories);

    if (dataForCategory.length > 0) {
        const c3Data = {
            bindto: binding,
            title: {
                text: title
            },
            data: {
                labels: true,
                json: dataForCategory,
                keys: {
                    x: 'name',
                    value: ['actual', 'budget']
                },
                type: 'bar',
                color: (color, d) => {
                    return colourtron(d, dataForCategory)
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

monthlyReportData().then((result => {
    generateCategory("In your home", result.categories, "#in-your-home", 1000);
    generateCategory("Insurance", result.categories, "#insurance");
    generateCategory("Motoring and public transport", result.categories, "#motoring-and-public-transport");
    generateCategory("Savings and investments", result.categories, "#savings-and-investments");
    generateCategory("Family", result.categories, "#family");
    generateCategory("Fun", result.categories, "#fun");
    generateCategory("Health and beauty", result.categories, "#health-and-beauty");
    generateCategory("Clothes", result.categories, "#clothes");
    generateCategory("Big one offs", result.categories, "#big-one-offs");
    generateCategory("Odds and sods", result.categories, "#odds-and-sods");
    generateCategory("Refurbishments", result.categories, "#refurbishments");
}));
