const colourtron = (d, dataForCategory) => {
    if (d === 'budget' || d.id === 'budget') {
        return '#cce5ff'
    } else if (d.id === 'actual') {
        const budget = dataForCategory[d.index].budget;
        return d.value > budget ? '#ff0000' : '#33cc33';
    }
};

module.exports = colourtron;
