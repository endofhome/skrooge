const dataForCategory = require('../../main/javascript/dataFinder');

test('Can find data if it exists for the specified category', () => {
    const expectedData = [{name: 'required data'}];
    const title = 'Worm farming';
    const categories = [{title: 'Blithering', data: []}, {title: 'Worm farming', data: expectedData}, {title: 'Blathering', data: []}];

    expect(dataForCategory(title, categories)).toBe(expectedData);
});

test('Returns an empty array if no matching category', () => {
    const title = 'Missing';
    const categories = [{title: 'Worm farming', data: [{name: 'Something about worm farming'}]}];

    expect(dataForCategory(title, categories)).toEqual([]);
});

test('Throws if more than one matching category', () => {
    const title = 'Worm farming';
    const categories = [{title: title, data: [{name: 'A'}]}, {title: title, data: [{name: 'B'}]}];
    const expectedError = {
        name: 'Too many matching categories',
        message: `2 categories are titled 'Worm farming'`
    };
    expect(() => { dataForCategory(title, categories) }).toThrow(expectedError)
});
