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
        throw {
            name: 'No matching categories',
            message: 'No matching categories for title:' + title
        }
    } else if (matchingCategories.length > 1) {
        throw {
            name: 'Too many matching categories',
            message: matchingCategories.length + ' matching categories for title: ' + title
        };
    }
};

function generateCategory(title, categories, binding, height) {
    const dataForCategory = _dataForCategory(title, categories);

    const c3Data = {
        bindto: binding,
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

monthlyReportData().then((result => {
    generateCategory("In your home", result.categories, "#in-your-home", 1000);
}));
