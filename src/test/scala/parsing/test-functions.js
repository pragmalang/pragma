function hello() {
    console.log('Hello from test-functions!')
}

const f = x => x + 1

function addVectors(a, b) {
    return ({ x: a.x + b.x, y: a.y + b.y })
}

function validateCat(cat) {
    return cat.name.length < 20
}

function isOwner({ user, todo }) {
    return user.todos.includes(todo)
}