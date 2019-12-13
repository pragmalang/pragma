function hello() {
    console.log('Hello from test-functions!')
}

const f = x => x + 1

function addVectors(a, b) {
    return ({ x: a.x + b.x, y: a.y + b.y })
}