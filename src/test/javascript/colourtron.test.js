const colourtron = require('../../main/javascript/colourtron');

test('returns green as a default', () => {
    expect(colourtron()).toBe("#33cc33");
});