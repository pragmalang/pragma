module.exports = {
  docsSideBar: {
    'Overview': [
      'introduction',
      'overview/install-pragma',
      {
        type: 'category',
        label: 'Getting Started',
        items: [
          'overview/getting-started/basic-todo-app',
          'overview/getting-started/data-validation-and-transformation'
        ]
      }
    ],
    'Language Features': [
      'features/models',
      'features/user-models',
      'features/functions',
      'features/directives',
      'features/permissions',
      'features/enums',
      'features/primitive-types',
    ],
    'The Generated API': ['api/api'],
  }
};
