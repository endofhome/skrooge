const colourtron = require('../../main/javascript/colourtron');

test('returns green for actual column when under budget', () => {
    const d = { index: 0, id: 'actual', value: 5 };
    const dataForCategory = [ { budget: 6 } ];

    expect(colourtron(d, dataForCategory)).toBe("#33cc33");
});

test('returns green for actual column when on budget', () => {
    const d = { index: 0, id: 'actual', value: 5 };
    const dataForCategory = [ { budget: 5 } ];

    expect(colourtron(d, dataForCategory)).toBe("#33cc33");
});

test('returns red for actual column when over budget', () => {
    const d = { index: 0, id: 'actual', value: 6 };
    const dataForCategory = [ { budget: 5 } ];

    expect(colourtron(d, dataForCategory)).toBe("#ff0000");
});

test('returns pale blue for budget column when over budget', () => {
    const d = { index: 0, id: 'budget', value: 6 };
    const dataForCategory = [];

    expect(colourtron(d, dataForCategory)).toBe("#cce5ff");
});

test('returns pale blue for budget column if no data in d parameter', () => {
    const d = 'budget';
    const dataForCategory = [];

    expect(colourtron(d, dataForCategory)).toBe("#cce5ff");
});

test('returns grey for annual budget column', () => {
    const d = { index: 0, id: 'annualBudget', value: 150 };
    const dataForCategory = [ { budget: 175 } ];

    expect(colourtron(d, dataForCategory)).toBe("#808080");
});
