const prependMrToUsername = user =>
    ({ ...user, username: "Mr. " + user.username })

function setPriorityTodo(user) {
    const firstTodo = user.todos[0]
    const newPriorityTodo = firstTodo ? firstTodo : null
    return {
        ...user,
        priorityTodo: {
            // Should refer to the existing record created within the `todos` array
            title: newPriorityTodo.title
        }
    }
}

function emphasizeUndone(todo) {
    if (todo.title && !todo.done)
        return { ...todo, title: '** ' + todo.title + ' **' }
    else
        return todo
}