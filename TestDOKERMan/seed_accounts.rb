# Local-only GitLab bootstrap, run inside the newly created Docker service.
require 'json'
settings = JSON.parse(File.read('/bootstrap/accounts.json'))
ApplicationSetting.current.update!(signup_enabled: false)
output = {}
root = User.find_by_username('root')
[['serviceExtebtion', settings.fetch('service_password')], ['user1', settings.fetch('user_password')]].each do |username, password|
  user = User.find_by_username(username)
  unless user
    user = Users::BuildService.new(root, {
      username: username, name: username, email: "#{username.downcase}@manual.test",
      password: settings.fetch('service_password'), skip_confirmation: true,
      organization_id: Organizations::Organization.first.id
    }).execute
    user.save!
  end
  # The explicitly requested short password is limited to this loopback test instance.
  user.password = password
  user.password_confirmation = password
  user.password_expires_at = nil
  user.save!(validate: false)
  raise 'Local password verification failed' unless user.valid_password?(password)
  scopes = username == 'user1' ? ['api', 'read_repository', 'write_repository'] : ['read_api', 'read_repository']
  token = user.personal_access_tokens.create!(name: 'ODS manual test', scopes: scopes, expires_at: 90.days.from_now)
  output[username] = {id: user.id, token: token.token}
end
token = root.personal_access_tokens.create!(name: 'ODS local bootstrap', scopes: ['api'], expires_at: 90.days.from_now)
output['root'] = {id: root.id, token: token.token}
File.write('/tmp/ods-accounts.json', JSON.generate(output))
File.chmod(0600, '/tmp/ods-accounts.json')
puts 'Local accounts and scoped tokens created; values written privately.'
