const colourtron = (d, dataForCategory) => {
    if (!d.id && d === 'budget' || d.id === 'budget') {
        return '#cce5ff'
    } else if (d.id) {
        const budget = dataForCategory[d.index].budget;
        return d.id === 'actual' && d.value > budget ? '#ff0000' : '#33cc33';
    }
};

module.exports = colourtron;
