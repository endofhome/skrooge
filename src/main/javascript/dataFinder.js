const dataForCategory = (title, categories) => {
    const matchingCategories = categories.filter(item => item.title === title);
    if (matchingCategories.length === 1) {
        return matchingCategories[0].data
    } else if (matchingCategories.length === 0) {
        console.log('No matching categories for title:' + title);
        return []
    } else if (matchingCategories.length > 1) {
        throw {
            name: 'Too many matching categories',
            message: `${matchingCategories.length} categories are titled '${title}'`
        };
    }
};

module.exports = dataForCategory;
