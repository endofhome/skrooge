const dataForCategory = (title, categories) => {
    const matchingCategories = categories.filter(item => item.title === title);
    if (matchingCategories.length > 1) {
        throw {
            name: 'Too many matching categories',
            message: `${matchingCategories.length} categories are titled '${title}'`
        };
    }
    return matchingCategories.length === 1 ? matchingCategories[0].data : []
};

module.exports = dataForCategory;
